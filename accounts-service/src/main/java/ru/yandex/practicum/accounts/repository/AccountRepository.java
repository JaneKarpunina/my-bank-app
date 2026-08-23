package ru.yandex.practicum.accounts.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.accounts.entity.BankAccount;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<BankAccount, Long> {

    Optional<BankAccount> findByUsername(String username);

    List<BankAccount> findAllByUsernameNot(String username);
}
