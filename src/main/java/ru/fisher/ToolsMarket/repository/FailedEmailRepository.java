package ru.fisher.ToolsMarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.fisher.ToolsMarket.models.FailedEmail;

@Repository
public interface FailedEmailRepository extends JpaRepository<FailedEmail, Long> {
}
