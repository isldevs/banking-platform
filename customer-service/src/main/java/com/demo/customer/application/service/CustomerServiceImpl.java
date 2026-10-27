package com.demo.customer.application.service;

import com.demo.customer.api.dto.request.NewCustomer;
import com.demo.customer.api.dto.response.CustomerResponse;
import com.demo.customer.application.mapper.CustomerMapper;
import com.demo.customer.application.port.in.CustomerService;
import com.demo.customer.common.Support;
import com.demo.customer.domain.model.Customer;
import com.demo.customer.domain.model.CustomerKyc;
import com.demo.customer.domain.model.CustomerStatus;
import com.demo.customer.domain.model.CustomerStatusHistory;
import com.demo.customer.domain.model.KycVerificationStatus;
import com.demo.customer.infrastructure.persistence.CustomerKycRepository;
import com.demo.customer.infrastructure.persistence.CustomerRepository;
import com.demo.customer.infrastructure.persistence.CustomerStatusHistoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerKycRepository customerKycRepository;
    private final CustomerStatusHistoryRepository statusHistoryRepository;
    private final CustomerMapper customerMapper;
    private final Support support;

    public CustomerServiceImpl(
            CustomerRepository customerRepository,
            CustomerKycRepository customerKycRepository,
            CustomerStatusHistoryRepository statusHistoryRepository,
            CustomerMapper customerMapper,
            Support support) {
        this.customerRepository = customerRepository;
        this.customerKycRepository = customerKycRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.customerMapper = customerMapper;
        this.support = support;
    }

    @Override
    @Transactional
    public CustomerResponse create(NewCustomer request, Jwt jwt) {
        UUID userId = subjectId(jwt);
        UUID id = UUID.randomUUID();
        String number = "CUS-" + id.toString().substring(0, 12).toUpperCase(Locale.ROOT);

        Customer customer = customerMapper.toEntity(request);
        customer.setId(id);
        customer.setCustomerNumber(number);
        customer.setUserId(userId);
        customer.setStatus(CustomerStatus.PENDING_KYC);

        Customer saved = customerRepository.save(customer);

        CustomerKyc kyc = new CustomerKyc();
        kyc.setId(UUID.randomUUID());
        kyc.setCustomerId(saved.getId());
        kyc.setKycLevel("BASIC");
        kyc.setVerificationStatus(KycVerificationStatus.PENDING);
        customerKycRepository.save(kyc);

        CustomerStatusHistory history = new CustomerStatusHistory();
        history.setId(UUID.randomUUID());
        history.setCustomerId(saved.getId());
        history.setNewStatus(CustomerStatus.PENDING_KYC);
        history.setChangedBy(userId);
        statusHistoryRepository.save(history);

        return customerMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse read(UUID id, Jwt jwt) {
        Customer customer = findCustomer(id);
        boolean ownsCustomer = customer.getUserId().equals(subjectId(jwt));
        boolean isAdmin = jwt.getClaimAsStringList("roles") != null
                && jwt.getClaimAsStringList("roles").contains("ADMIN");
        if (!ownsCustomer && !isAdmin) {
            throw Support.fail(HttpStatus.FORBIDDEN, "NOT_CUSTOMER_OWNER");
        }
        return customerMapper.toResponse(customer);
    }

    @Override
    @Transactional
    public CustomerResponse approve(UUID id, Jwt jwt) {
        Customer customer = findCustomer(id);
        if (customer.getStatus() != CustomerStatus.PENDING_KYC) {
            throw Support.fail(HttpStatus.CONFLICT, "INVALID_KYC_STATE");
        }

        CustomerKyc kyc = customerKycRepository.findByCustomerId(id)
                .orElseThrow(() -> Support.fail(HttpStatus.CONFLICT, "CUSTOMER_KYC_NOT_FOUND"));

        UUID reviewerId = subjectId(jwt);
        Instant now = Instant.now();
        customer.setStatus(CustomerStatus.ACTIVE);
        kyc.setVerificationStatus(KycVerificationStatus.APPROVED);
        kyc.setVerifiedAt(now);
        kyc.setVerifiedBy(reviewerId);

        // Both entities are managed in this transaction; dirty checking persists them atomically.
        customerKycRepository.save(kyc);

        CustomerStatusHistory history = new CustomerStatusHistory();
        history.setId(UUID.randomUUID());
        history.setCustomerId(id);
        history.setOldStatus(CustomerStatus.PENDING_KYC);
        history.setNewStatus(CustomerStatus.ACTIVE);
        history.setReason("KYC_APPROVED");
        history.setChangedBy(reviewerId);
        statusHistoryRepository.save(history);

        return customerMapper.toResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse internal(UUID id, String key) {
        support.internal(key);
        return customerMapper.toResponse(findCustomer(id));
    }

    private Customer findCustomer(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> Support.fail(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND"));
    }

    private UUID subjectId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException ex) {
            throw Support.fail(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN_SUBJECT");
        }
    }
}
