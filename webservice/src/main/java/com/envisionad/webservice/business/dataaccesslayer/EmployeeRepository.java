package com.envisionad.webservice.business.dataaccesslayer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, Integer> {
    List<Employee> findAllByBusinessId_BusinessId(String businessId);

    /**
     * The batch form of the finder above, for resolving one contact per business across many
     * businesses at once (P6 M2a's media-owner notifications). Ordered by id so that "the first
     * employee of this business" is a stable choice rather than whatever order the database
     * happens to return — the unordered single-business finder relied on that implicitly.
     */
    List<Employee> findAllByBusinessId_BusinessIdInOrderById(Collection<String> businessIds);
    Employee findByUserId(String employeeId);
    boolean existsByUserIdAndBusinessId_BusinessId(String userId, String businessId);
    boolean existsByUserId(String userId);

}
