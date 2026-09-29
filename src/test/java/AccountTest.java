import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {
    @TempDir
    Path temporaryFolder;

    private Account newAccount() {
        return new Account(
                "Khushi",
                "AC1001",
                "1234",
                new BigDecimal("100.00")
        );
    }

    @Test
    void verifiesPin() {
        Account account = newAccount();

        assertTrue(account.verifyPin("1234"));
        assertFalse(account.verifyPin("9999"));
    }

    @Test
    void changesPinOnlyWithCorrectCurrentPin() throws IOException {
        Account account = newAccount();
        Path pinFile = temporaryFolder.resolve("pin.txt");

        assertFalse(account.changePin("9999", "5678", pinFile));
        assertTrue(account.verifyPin("1234"));

        assertTrue(account.changePin("1234", "5678", pinFile));
        assertFalse(account.verifyPin("1234"));
        assertTrue(account.verifyPin("5678"));
    }

    @Test
    void rejectsInvalidDeposits() {
        Account account = newAccount();

        assertFalse(account.deposit(new BigDecimal("-5")));
        assertFalse(account.deposit(BigDecimal.ZERO));
        assertFalse(account.deposit(new BigDecimal("1.999")));
        assertFalse(account.deposit(null));

        assertEquals(
                new BigDecimal("100.00"),
                account.getBalance()
        );
    }

    @Test
    void rejectsInvalidOrExcessiveWithdrawals() {
        Account account = newAccount();

        assertFalse(account.withdraw(new BigDecimal("-1")));
        assertFalse(account.withdraw(BigDecimal.ZERO));
        assertFalse(account.withdraw(new BigDecimal("10.999")));
        assertFalse(account.withdraw(new BigDecimal("200")));

        assertEquals(
                new BigDecimal("100.00"),
                account.getBalance()
        );
    }

    @Test
    void depositAndWithdrawalUpdateBalance() {
        Account account = newAccount();

        assertTrue(account.deposit(new BigDecimal("50.00")));
        assertTrue(account.withdraw(new BigDecimal("25.00")));

        assertEquals(
                new BigDecimal("125.00"),
                account.getBalance()
        );

        assertEquals(3, account.getTransactionHistory().size());
    }

    @Test
    void transferUpdatesBothAccountsAndHistories() {
        Account sender = newAccount();
        Account recipient = new Account(
                "Friend",
                "AC1002",
                "",
                BigDecimal.ZERO
        );

        assertFalse(
                sender.transferTo(recipient, new BigDecimal("150.00"))
        );

        assertTrue(
                sender.transferTo(recipient, new BigDecimal("40.00"))
        );

        assertEquals(
                new BigDecimal("60.00"),
                sender.getBalance()
        );
        assertEquals(
                new BigDecimal("40.00"),
                recipient.getBalance()
        );

        assertEquals(
                "TRANSFER_OUT",
                sender.getTransactionHistory().get(1).type()
        );
        assertEquals(
                "TRANSFER_IN",
                recipient.getTransactionHistory().get(1).type()
        );
    }

    @Test
    void savedAccountLoadsBalanceAndHistory() throws IOException {
        Path dataFile = temporaryFolder.resolve("account-data.txt");

        Account original = newAccount();
        original.deposit(new BigDecimal("20.00"));
        original.saveToFile(dataFile);

        Account reopened = newAccount();
        reopened.loadFromFile(dataFile);

        assertEquals(
                new BigDecimal("120.00"),
                reopened.getBalance()
        );
        assertEquals(2, reopened.getTransactionHistory().size());
        assertEquals(
                "DEPOSIT",
                reopened.getTransactionHistory().get(1).type()
        );
    }
}