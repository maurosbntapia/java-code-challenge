# Transactions Service

Servicio RESTful que almacena transacciones **en memoria** y permite consultarlas por tipo y sumar una transacción junto con todos sus descendientes.

Spring Boot 3 · Java 21 · sin SQL. El diseño completo y el porqué de cada decisión están en [design.md](design.md).

## Cómo correrlo

### Con Docker (recomendado)

```bash
docker compose up --build
```

El servicio queda en `http://localhost:8080`. La imagen compila y corre todos los tests durante el build.

### Local

Requiere JDK 21 y Maven 3.9+.

```bash
mvn spring-boot:run   # levanta la app en :8080
mvn test              # corre unitarios + integración
```

### Documentación interactiva

| URL | Contenido |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Swagger UI |
| `http://localhost:8080/api-docs` | Spec OpenAPI (JSON) |
| `http://localhost:8080/actuator/health` | Healthcheck |

## API

### `PUT /transactions/{transaction_id}`

```json
{ "amount": 5000, "type": "cars", "parent_id": 10 }
```

`amount` (finito y `> 0`) y `type` (no vacío) son obligatorios; `parent_id` es opcional y, si viene, debe existir.

| Status | Cuándo |
|---|---|
| `201 Created` | Transacción nueva → `{"status":"ok"}` |
| `200 OK` | Reintento idéntico (idempotente) → `{"status":"ok"}` |
| `400 Bad Request` | Body inválido o JSON mal formado |
| `409 Conflict` | El id ya existe con otro contenido |
| `422 Unprocessable Entity` | `parent_id` inexistente |

### `GET /transactions/types/{type}`

Ids de las transacciones del tipo, en orden de creación (`[]` si no hay). El tipo no distingue mayúsculas: `/types/CARS` = `/types/cars`.

### `GET /transactions/sum/{transaction_id}`

Monto de la transacción más el de **todos sus descendientes** → `{"sum": 20000}`. `404` si no existe.

### Errores

Todos los errores usan [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457):

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Transaction 10 already exists with different content",
  "instance": "/transactions/10"
}
```

### Ejemplo

```bash
curl -X PUT localhost:8080/transactions/10 -H 'Content-Type: application/json' -d '{"amount":5000,"type":"cars"}'
curl -X PUT localhost:8080/transactions/11 -H 'Content-Type: application/json' -d '{"amount":10000,"type":"shopping","parent_id":10}'
curl -X PUT localhost:8080/transactions/12 -H 'Content-Type: application/json' -d '{"amount":5000,"type":"shopping","parent_id":11}'

curl localhost:8080/transactions/types/cars   # [10]
curl localhost:8080/transactions/sum/10       # {"sum":20000}
curl localhost:8080/transactions/sum/11       # {"sum":15000}
```

## Decisiones principales

| | Decisión | Detalle |
|---|---|---|
| D1 | `sum` = transacción + descendientes | Única lectura consistente con los ejemplos |
| D2 | Transacciones inmutables, `PUT` idempotente | Reintento idéntico → `200`; distinto → `409` |
| D3 | El padre debe existir | Junto con D2, hace imposibles los ciclos |
| D4 | Montos finitos y `> 0` | Se rechaza `1e309` (`Infinity`) |
| D5 | Tipo normalizado (`trim` + `Locale.ROOT`) | Al guardar y al consultar |
| D6 | `double` en el contrato, `BigDecimal` interno | `0.1 + 0.2 = 0.3` |

Ver [design.md §4](design.md#4-decisiones) para el análisis completo.

## Arquitectura

```
api             TransactionController, DTOs, GlobalExceptionHandler, OpenApiConfig
application     TransactionService
domain          Transaction, TransactionType, TransactionRepository (puerto), excepciones
infrastructure  InMemoryTransactionRepository (adapter)
```

El repositorio en memoria usa un `ReentrantReadWriteLock` que protege los tres índices (`byId`, `idsByType`, `childrenByParent`) como una unidad. Así `save` valida y escribe de forma atómica, y las lecturas corren en paralelo. La suma recorre los descendientes de forma iterativa, sin riesgo de `StackOverflowError` en cadenas profundas.

## Tests

| Suite | Qué cubre |
|---|---|
| `TransactionTypeTest`, `TransactionTest` | Normalización, validación de monto, igualdad de contenido |
| `InMemoryTransactionRepositoryTest` | Invariantes, índices, orden, recorrido de `sum` |
| `InMemoryTransactionRepositoryConcurrencyTest` | Escrituras concurrentes, lecturas durante escrituras, cadena de 10.000 niveles |
| `TransactionControllerIntegrationTest` | Contrato HTTP con JSON crudo, ejemplo de aceptación |
| `TransactionErrorHandlingIntegrationTest` | 400 / 404 / 409 / 422 como Problem Details, `PUT`s concurrentes |
| `OpenApiIntegrationTest` | Spec OpenAPI y Swagger UI |

## Limitaciones

- El estado vive en el heap: se pierde al reiniciar y no se comparte entre réplicas.
- Sin autenticación.
- Extensiones posibles (persistencia durable, reversas, ancestros) en [design.md §12](design.md#12-extensiones-futuras).
