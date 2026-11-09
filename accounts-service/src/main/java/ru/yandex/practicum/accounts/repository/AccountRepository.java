package ru.yandex.practicum.accounts.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.accounts.entity.BankAccount;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<BankAccount, Long> {

    Optional<BankAccount> findByUsername(String username);

    List<BankAccount> findAllByUsernameNot(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BankAccount b WHERE b.username = :username")
    Optional<BankAccount> findByUsernameForUpdate(String username);
}
