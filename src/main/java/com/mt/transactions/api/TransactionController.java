package com.mt.transactions.api;

import com.mt.transactions.api.dto.CreateTransactionRequest;
import com.mt.transactions.api.dto.StatusResponse;
import com.mt.transactions.api.dto.SumResponse;
import com.mt.transactions.application.TransactionService;
import com.mt.transactions.domain.SaveResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    /** Creates a transaction: 201 when new, 200 on an identical retry. */
    @PutMapping("/{transactionId}")
    public ResponseEntity<StatusResponse> putTransaction(
            @PathVariable long transactionId,
            @Valid @RequestBody CreateTransactionRequest request) {

        SaveResult result = service.createTransaction(
                transactionId, BigDecimal.valueOf(request.amount()), request.type(), request.parentId());

        HttpStatus status = result == SaveResult.CREATED ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(StatusResponse.ok());
    }

    /** Ids of all transactions of the given type, in creation order. The type is normalized. */
    @GetMapping("/types/{type}")
    public List<Long> getIdsByType(@PathVariable String type) {
        return service.getIdsByType(type);
    }

    /** Amount of the transaction plus all its descendants. */
    @GetMapping("/sum/{transactionId}")
    public SumResponse getSum(@PathVariable long transactionId) {
        return SumResponse.of(service.getSum(transactionId));
    }
}
