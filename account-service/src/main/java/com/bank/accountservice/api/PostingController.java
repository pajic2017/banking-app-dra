package com.bank.accountservice.api;
import com.bank.accountservice.service.PostingService;
import com.bank.contracts.posting.v1.*;
import org.springframework.web.bind.annotation.*;

/** Internal versioned protocol; deliberately absent from public Gateway routes. */
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('SERVICE_TRANSACTION')")
@RestController
@RequestMapping("/internal/v1/postings")
public class PostingController {
    private final PostingService postings;
    public PostingController(PostingService postings) { this.postings = postings; }
    @PostMapping
    public PostingResultV1 post(@RequestBody PostingCommandV1 command) { return postings.post(command); }
}
