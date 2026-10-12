package com.bank.transactionservice.events;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
public class CorrelationFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        UUID id;
        try {
            var header=request.getHeader("X-Correlation-ID");id=header==null?UUID.randomUUID():UUID.fromString(header);
            if(header!=null && !id.toString().equalsIgnoreCase(header)) throw new IllegalArgumentException();
        } catch(IllegalArgumentException invalid) {
            response.setStatus(400);response.setContentType("application/problem+json");
            response.getWriter().write("{\"status\":400,\"title\":\"Invalid correlation ID\",\"detail\":\"X-Correlation-ID must be a UUID.\"}");return;
        }
        request.setAttribute("bank.correlationId",id);response.setHeader("X-Correlation-ID",id.toString());
        var previous=MDC.get("correlationId");MDC.put("correlationId",id.toString());
        try { chain.doFilter(request,response); } finally { if(previous==null)MDC.remove("correlationId");else MDC.put("correlationId",previous); }
    }
}
