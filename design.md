# Transactions Service — RFC / Design Doc
 
> Estado: **Propuesto** · Autor: Mauro Tapia · Última actualización: 2026-11-06
 
## 1. Contexto y objetivo
 
Servicio web RESTful que almacena transacciones **en memoria** y expone consultas sobre ellas:
 
- Registrar una transacción con un tipo, un monto y, opcionalmente, una transacción padre.
- Listar los ids de todas las transacciones de un tipo.
- Calcular el monto total de una transacción y de todas las que dependen de ella.
### Restricciones
 
- Spring Boot, Java 11 o superior (se usa **Java 21**).
- **Sin SQL**: el almacenamiento es en estructuras de datos en memoria.
- Tests de integración, aplicación dockerizada y código claro.
## 2. Alcance
 
| En alcance | Fuera de alcance |
|---|---|
| Los 3 endpoints del contrato | Persistencia durable (el estado se pierde al reiniciar) |
| Validaciones y manejo de errores | Edición o borrado de transacciones |
| Concurrencia segura en una instancia | Múltiples instancias / estado distribuido |
| Docker + documentación | Autenticación / autorización |
| | Reversas, débitos/créditos (ver §12) |
 
## 3. Contrato de API
 
### 3.1 `PUT /transactions/{transaction_id}`
 
Request body:
 
```json
{ "amount": 5000, "type": "cars", "parent_id": 10 }
```
 
| Campo | Tipo | Requerido | Regla |
|---|---|---|---|
| `transaction_id` (path) | long | sí | Numérico |
| `amount` | double | sí | Finito y `> 0` |
| `type` | string | sí | No vacío; se normaliza (ver D5) |
| `parent_id` | long | no | Si viene, la transacción padre debe existir |
 
Respuestas:
 
| Caso | Status | Body |
|---|---|---|
| Transacción creada | `201 Created` | `{"status":"ok"}` |
| Reintento idéntico de una transacción existente | `200 OK` | `{"status":"ok"}` |
| Id existente con contenido distinto | `409 Conflict` | Problem Detail |
| `parent_id` inexistente | `422 Unprocessable Entity` | Problem Detail |
| Body inválido (campo faltante, monto ≤ 0, tipo vacío, JSON mal formado) | `400 Bad Request` | Problem Detail |
 
### 3.2 `GET /transactions/types/{type}`
 
Devuelve la lista JSON de ids de las transacciones del tipo, en orden de creación.
 
| Caso | Status | Body |
|---|---|---|
| Tipo con transacciones | `200 OK` | `[10, 11]` |
| Tipo sin transacciones | `200 OK` | `[]` |
 
El `type` del path se normaliza igual que al guardar: `GET /transactions/types/CARS` es equivalente a `/cars`.
 
### 3.3 `GET /transactions/sum/{transaction_id}`
 
Devuelve la suma del monto de la transacción más el de **todos sus descendientes** (hijos, nietos, etc.).
 
| Caso | Status | Body |
|---|---|---|
| Transacción existente | `200 OK` | `{"sum": 20000}` |
| Transacción inexistente | `404 Not Found` | Problem Detail |
 
### 3.4 Formato de errores
 
Se usa **RFC 9457 Problem Details**, soportado de forma nativa por Spring Boot 3:
 
```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Transaction 10 already exists with different content",
  "instance": "/transactions/10"
}
```
 
### 3.5 Ejemplo de aceptación
 
```
PUT /transactions/10 { "amount": 5000,  "type": "cars" }                    → 201 {"status":"ok"}
PUT /transactions/11 { "amount": 10000, "type": "shopping", "parent_id": 10 } → 201 {"status":"ok"}
PUT /transactions/12 { "amount": 5000,  "type": "shopping", "parent_id": 11 } → 201 {"status":"ok"}
 
GET /transactions/types/cars → [10]
GET /transactions/sum/10     → {"sum": 20000}
GET /transactions/sum/11     → {"sum": 15000}
```
 
## 4. Decisiones
 
### D1 — Semántica de `sum`: transacción + descendientes
 
"Transitivamente conectadas" admite tres lecturas: descendientes, ancestros o el árbol completo.
 
| Interpretación | sum(10) | sum(11) | ¿Consistente con los ejemplos? |
|---|---|---|---|
| **Self + descendientes** | 20000 | 15000 | ✅ |
| Self + ancestros | 5000 | 15000 | ❌ |
| Árbol completo | 20000 | 20000 | ❌ |
 
Se elige **self + descendientes**, la única lectura consistente con ambos ejemplos.
 
> Nota: `sum(11) = 15000` por sí solo es ambiguo, porque 10 y 12 tienen el mismo monto. El ejemplo que desambigua es `sum(10) = 20000`.
 
### D2 — Transacciones inmutables con PUT idempotente
 
Una transacción **no se edita** una vez creada, igual que un movimiento en un ledger real: las correcciones se hacen con transacciones nuevas.
 
- Mismo id y mismo contenido → `200`, no-op (reintento seguro ante timeouts).
- Mismo id y contenido distinto → `409 Conflict`.
**Por qué PUT y no POST:** el cliente define el id en la URI. PUT es idempotente, de modo que un reintento no duplica la transacción, cosa que sí pasaría con POST.
 
**Trade-off:** se pierde la posibilidad de corregir una transacción. A cambio, desaparecen la reindexación, el movimiento de subárboles y toda una clase de bugs de consistencia.
 
### D3 — El padre debe existir
 
Si `parent_id` no corresponde a una transacción existente, se responde `422`.
 
**Consecuencia clave:** combinada con D2, esta regla hace que los **ciclos sean estructuralmente imposibles**. Una transacción nueva solo puede apuntar a una que ya existe, y nada existente puede apuntar a ella porque no se puede editar. `parent_id == transaction_id` queda rechazado automáticamente, porque el padre todavía no existe.
 
Se usa `422` y no `404` porque el recurso de la URI es válido: lo que referencia a algo inexistente es el contenido del body.
 
**Trade-off:** el cliente debe cargar padre antes que hijo.
 
### D4 — Montos estrictamente positivos
 
`amount` debe ser finito y `> 0`. Los ejemplos describen consumos y el enunciado habla de "monto involucrado", es decir, una magnitud. Las restas no se modelan como montos negativos (ver §12).
 
Se rechazan también valores no finitos: un literal como `1e309` se deserializa como `Infinity`.
 
### D5 — Normalización del tipo
 
`type` se normaliza con `trim()` y `toLowerCase(Locale.ROOT)`, **tanto al guardar como al consultar**. `"CARS"`, `" Cars "` y `"cars"` son el mismo tipo.
 
Se usa `Locale.ROOT` para que el resultado no dependa del locale del servidor (por ejemplo, la "I" en turco).
 
### D6 — `double` en el contrato, `BigDecimal` interno
 
El contrato expone `double`, pero los montos se guardan y suman como `BigDecimal` para evitar errores de punto flotante (`0.1 + 0.2 ≠ 0.3`).
 
La comparación de montos en el reintento idempotente usa `compareTo`, no `equals`, porque `equals` tiene en cuenta la escala (`5000` ≠ `5000.0`).

La respuesta de `sum` también es `BigDecimal`, sin ceros a la derecha y en notación plana: `20000` (igual que el ejemplo del enunciado), `0.3` y `10000000`. Devolver `double` daría `20000.0` y, para montos grandes, notación científica (`1.0E7`).
 
### D7 — Nombres en snake_case
 
`parent_id` se mapea explícitamente con `@JsonProperty`. Sin ese mapeo, Jackson lo ignora en silencio y la transacción queda sin padre.
 
### D8 — Orden determinístico
 
Los ids por tipo y los hijos de cada transacción se guardan en `LinkedHashSet`, que preserva el orden de inserción y hace las respuestas reproducibles.
 
## 5. Modelo de dominio
 
```java
record Transaction(long id, BigDecimal amount, TransactionType type, Long parentId) {
    boolean sameContentAs(Transaction other) { ... } // amount con compareTo
}
 
record TransactionType(String value) {
    // constructor compacto: valida no vacío y normaliza
}
```
 
Las reglas propias del dato (tipo normalizado, monto válido) viven en el dominio. Así es imposible construir una transacción inválida.
 
## 6. Arquitectura
 
Capas livianas, sin sobreingeniería:
 
```
api             TransactionController, DTOs, GlobalExceptionHandler
   ↓
application     TransactionService (casos de uso)
   ↓
domain          Transaction, TransactionType, excepciones,
                TransactionRepository (interfaz / puerto)
   ↑
infrastructure  InMemoryTransactionRepository (adapter)
```
 
- El service depende de la **interfaz** `TransactionRepository`, no de la implementación en memoria. Cambiar a Redis o Mongo no toca el service (DIP / Open-Closed).
- Los DTOs de la API están separados del dominio, así que el contrato JSON puede evolucionar sin tocar el modelo.
### Ubicación de las invariantes de integridad
 
La validación de "el padre existe" y "el id no existe con otro contenido" tiene que ser **atómica con la escritura**. Si se valida en un paso y se escribe en otro, dos requests concurrentes pueden pasar la validación a la vez.
 
Por eso el repositorio expone una operación atómica `save(Transaction) → SaveResult` (`CREATED` / `REPLAYED`) que garantiza esas invariantes, igual que una base de datos garantiza una foreign key o una unique constraint. El service orquesta y traduce resultados; no maneja locks.
 
## 7. Almacenamiento en memoria
 
No hay schema: los datos viven en el heap de la JVM y se pierden al detener el proceso.
 
| Estructura | Uso |
|---|---|
| `Map<Long, Transaction> byId` | Almacenamiento principal |
| `Map<TransactionType, Set<Long>> idsByType` | `GET /types/{type}` en O(1) |
| `Map<Long, Set<Long>> childrenByParent` | Recorrido de descendientes en `sum` |
 
### Concurrencia
 
- Un `ReentrantReadWriteLock` protege las tres estructuras como una unidad.
- `save` toma el **write lock**: validación y escritura en las tres estructuras ocurren de forma atómica.
- Las lecturas toman el **read lock** y pueden ejecutarse en paralelo.
- Como todo acceso está bajo el lock, se usan `HashMap` / `LinkedHashSet` comunes. `ConcurrentHashMap` no garantiza atomicidad entre varias estructuras, así que no aportaría nada.
### Algoritmo de `sum`
 
Recorrido **iterativo** (DFS con `ArrayDeque`) desde la transacción pedida a través de `childrenByParent`, acumulando en `BigDecimal`.
 
- Iterativo y no recursivo, para que una cadena muy profunda no provoque `StackOverflowError`.
- Por D2 + D3 la estructura es un bosque sin ciclos. Igual se mantiene un set de visitados como defensa en profundidad.
### Complejidad
 
| Operación | Tiempo |
|---|---|
| `PUT` | O(1) |
| `GET /types/{type}` | O(k), k = ids del tipo |
| `GET /sum/{id}` | O(d), d = cantidad de descendientes |
 
Alternativa descartada: precalcular las sumas propagando a los ancestros en cada escritura. Eso da `sum` en O(1), pero lleva el `PUT` a O(profundidad) y agrega estado derivado que hay que mantener consistente. No se justifica para este alcance.
 
## 8. Estrategia de testing
 
Se desarrolla con **TDD**: cada comportamiento arranca con un test que falla.
 
| Nivel | Qué cubre | Herramientas |
|---|---|---|
| Unitario — dominio | Normalización de tipo, validación de monto, `sameContentAs` | JUnit 5, AssertJ |
| Unitario — repositorio | Índices, invariantes, recorrido, concurrencia | JUnit 5, `ExecutorService` |
| Integración | Contrato HTTP completo con el contexto de Spring | `@SpringBootTest` + MockMvc |
 
Los tests de integración envían **JSON crudo idéntico al del contrato**, no DTOs serializados por el propio código. Si no, un error de mapeo como el de D7 pasaría desapercibido. El estado en memoria se limpia antes de cada test para evitar dependencias de orden.
 
### Casos obligatorios
 
| Caso | Esperado |
|---|---|
| Ejemplo de aceptación (§3.5) | `[10]`, `{"sum":20000}`, `{"sum":15000}` (body literal) |
| `parent_id` en snake_case vincula al padre | sum del padre incluye al hijo |
| `0.1 + 0.2` | `0.3` |
| Reintento idéntico | `200`, sin duplicar en `/types` |
| Mismo id con contenido distinto | `409` |
| `5000` vs `5000.0` en reintento | `200` (mismo contenido) |
| `parent_id` inexistente | `422` |
| `parent_id == transaction_id` | `422` |
| `amount` faltante, `0`, negativo o `1e309` | `400` |
| `type` faltante, vacío o solo espacios | `400` |
| `CARS` y `cars` | mismo tipo, al guardar y al consultar |
| Tipo sin transacciones | `200 []` |
| Sum de id inexistente | `404` |
| Cadena de 10.000 niveles | sin `StackOverflowError` |
| PUTs concurrentes con el mismo id y distinto contenido | uno `201`, el resto `409` |
 
## 9. Plan de implementación (commits)
 
Cada paso es un par *test rojo → implementación*, con [Conventional Commits](https://www.conventionalcommits.org/).
 
| # | Commit | Contenido |
|---|---|---|
| 1 | `docs: add design doc` | Este documento |
| 2 | `chore: bootstrap spring boot project` | Maven, Java 21, Boot 3, estructura de paquetes |
| 3 | `feat(domain): add transaction type normalization` | `TransactionType` + tests |
| 4 | `feat(domain): add transaction model and amount validation` | `Transaction` + tests |
| 5 | `feat(infra): add in-memory repository with save` | Índices, `CREATED`/`REPLAYED`/conflicto, padre obligatorio |
| 6 | `feat(api): add PUT /transactions/{id}` | Controller, DTO, test de integración |
| 7 | `feat(api): add GET /transactions/types/{type}` | Test de integración primero |
| 8 | `feat: add GET /transactions/sum/{id}` | Recorrido iterativo + ejemplo de aceptación |
| 9 | `feat(api): add problem details error handling` | 400 / 404 / 409 / 422 |
| 10 | `test: add concurrency and deep chain tests` | `ExecutorService`, 10.000 niveles |
| 11 | `build: add multi-stage dockerfile` | Docker + docker compose |
| 12 | `docs: add readme and openapi` | Cómo correr, decisiones, springdoc |
 
## 10. Stack y build
 
- Java 21, Spring Boot 3.x, Maven.
- `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`.
- springdoc-openapi para Swagger UI.
- Tests: JUnit 5, AssertJ, MockMvc (incluidos en `spring-boot-starter-test`).
### Docker
 
- Build **multi-stage**: imagen con Maven + JDK 21 para compilar y correr tests, imagen final solo con JRE 21.
- La app corre con un usuario **no root**.
- Puerto `8080`, healthcheck vía `/actuator/health`.
- `docker compose up` como forma única de levantarla.
## 11. Supuestos
 
- Una sola instancia del servicio: el estado no se comparte entre réplicas.
- Se acepta cualquier valor `long` como id; el enunciado no lo restringe.
- Los campos desconocidos del body se ignoran.
- La suma se devuelve como número JSON exacto y sin ceros a la derecha (`20000`, `0.3`), idéntico al ejemplo del enunciado.
## 12. Extensiones futuras
 
- **Reversas y ajustes.** Hoy `amount > 0`. Si el negocio necesitara restas, no se usarían montos negativos ni un endpoint de "resta". Se modelarían como en un ledger de partida doble, con una dirección explícita (`DEBIT` / `CREDIT`) y monto siempre positivo, o como transacciones de reversa vinculadas a la original vía `parent_id`. `sum` pasaría a calcular el neto según la dirección.
- **Consulta de ancestros** (`GET /transactions/{id}/ancestors`) para auditoría: reconstruir la cadena que originó una transacción.
- **Persistencia durable**: una nueva implementación de `TransactionRepository` (por ejemplo Redis o MongoDB) sin cambios en el service.
- **Múltiples instancias**: requiere estado externo compartido y mover las invariantes de atomicidad a ese almacenamiento.
- **Sumas precalculadas** si `sum` se volviera un cuello de botella (ver §7).

