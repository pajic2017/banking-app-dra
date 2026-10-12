package com.bank.contracts.posting.v1;
import java.time.Instant;
import java.util.UUID;
public record PostingResultV1(UUID operationId, PostingStatus status, String failureCode,
                              Instant recordedAt, String instructionHash) { }
