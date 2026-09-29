import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Account {
    // A record is a compact class for holding transaction data.
    public record Transaction(
            long id,
            String dateTime,
            String type,
            BigDecimal amount,
            BigDecimal resultingBalance,
            String note
    ) {}

    private final String accountHolder;
    private final String accountNumber;
    private String pin;

    private BigDecimal balance;
    private final ArrayList<Transaction> transactions = new ArrayList<>();
    private long nextTransactionId = 1;

    public Account(String accountHolder, String accountNumber,
                   String pin, BigDecimal startingBalance) {
        if (startingBalance == null || startingBalance.signum() < 0) {
            throw new IllegalArgumentException(
                    "Starting balance cannot be negative."
            );
        }

        this.accountHolder = accountHolder;
        this.accountNumber = accountNumber;
        this.pin = pin;
        this.balance = startingBalance.setScale(2, RoundingMode.UNNECESSARY);

        recordTransaction(
                "OPENING",
                this.balance,
                "Account opened"
        );
    }

    public String getAccountHolder() {
        return accountHolder;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public List<Transaction> getTransactionHistory() {
        return List.copyOf(transactions);
    }

    public boolean verifyPin(String enteredPin) {
        return pin != null && pin.equals(enteredPin);
    }

    public boolean changePin(String currentPin, String newPin,
                             Path pinFile) throws IOException {
        if (!verifyPin(currentPin)
                || newPin == null
                || !newPin.matches("\\d{4}")
                || newPin.equals(currentPin)) {
            return false;
        }

        Files.writeString(pinFile, newPin);
        pin = newPin;
        return true;
    }

    public boolean deposit(BigDecimal amount) {
        if (!isValidAmount(amount)) {
            return false;
        }

        BigDecimal money = amount.setScale(2, RoundingMode.UNNECESSARY);
        balance = balance.add(money);

        recordTransaction("DEPOSIT", money, "");
        return true;
    }

    public boolean withdraw(BigDecimal amount) {
        if (!isValidAmount(amount)) {
            return false;
        }

        BigDecimal money = amount.setScale(2, RoundingMode.UNNECESSARY);

        if (money.compareTo(balance) > 0) {
            return false;
        }

        balance = balance.subtract(money);

        recordTransaction("WITHDRAWAL", money, "");
        return true;
    }

    public boolean transferTo(Account recipient, BigDecimal amount) {
        if (recipient == null
                || recipient == this
                || recipient.accountNumber.equals(this.accountNumber)
                || !isValidAmount(amount)) {
            return false;
        }

        BigDecimal money = amount.setScale(2, RoundingMode.UNNECESSARY);

        if (money.compareTo(balance) > 0) {
            return false;
        }

        balance = balance.subtract(money);
        recipient.balance = recipient.balance.add(money);

        recordTransaction(
                "TRANSFER_OUT",
                money,
                "To " + recipient.accountNumber
        );

        recipient.recordTransaction(
                "TRANSFER_IN",
                money,
                "From " + this.accountNumber
        );

        return true;
    }

    private boolean isValidAmount(BigDecimal amount) {
        return amount != null
                && amount.signum() > 0
                && amount.stripTrailingZeros().scale() <= 2;
    }

    private void recordTransaction(String type, BigDecimal amount,
                                   String note) {
        transactions.add(new Transaction(
                nextTransactionId++,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                type,
                amount,
                balance,
                note
        ));
    }

    public void saveToFile(Path file) throws IOException {
        Properties properties = new Properties();

        properties.setProperty("format", "2");
        properties.setProperty("accountNumber", accountNumber);
        properties.setProperty("balance", balance.toPlainString());
        properties.setProperty(
                "transaction.count",
                Integer.toString(transactions.size())
        );

        for (int i = 0; i < transactions.size(); i++) {
            Transaction transaction = transactions.get(i);
            String key = "transaction." + i + ".";

            properties.setProperty(key + "id",
                    Long.toString(transaction.id()));
            properties.setProperty(key + "dateTime",
                    transaction.dateTime());
            properties.setProperty(key + "type",
                    transaction.type());
            properties.setProperty(
                    key + "amount",
                    transaction.amount() == null
                            ? ""
                            : transaction.amount().toPlainString()
            );
            properties.setProperty(
                    key + "resultingBalance",
                    transaction.resultingBalance() == null
                            ? ""
                            : transaction.resultingBalance().toPlainString()
            );
            properties.setProperty(key + "note", transaction.note());
        }

        Path temporaryFile = file.resolveSibling(
                file.getFileName().toString() + ".tmp"
        );

        try (OutputStream output = Files.newOutputStream(temporaryFile)) {
            properties.store(output, "Bank account data");
        }

        Files.move(
                temporaryFile,
                file,
                StandardCopyOption.REPLACE_EXISTING
        );
    }

    public void loadFromFile(Path file) throws IOException {
        if (!Files.exists(file)) {
            return;
        }

        List<String> lines = Files.readAllLines(file);

        if (lines.isEmpty()) {
            throw new IOException("The account file is empty.");
        }

        // Read files made by the earlier version of this project.
        if (lines.get(0).matches("\\d+(\\.\\d+)?")) {
            loadOldFormat(lines);
            return;
        }

        Properties properties = new Properties();

        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }

        if (!"2".equals(properties.getProperty("format"))
                || !accountNumber.equals(
                properties.getProperty("accountNumber")
        )) {
            throw new IOException(
                    "The file format or account number is incorrect."
            );
        }

        try {
            BigDecimal loadedBalance = new BigDecimal(
                    properties.getProperty("balance")
            );

            if (loadedBalance.signum() < 0) {
                throw new IOException("The saved balance is invalid.");
            }

            int count = Integer.parseInt(
                    properties.getProperty("transaction.count")
            );

            ArrayList<Transaction> loadedTransactions =
                    new ArrayList<>();

            long highestId = 0;

            for (int i = 0; i < count; i++) {
                String key = "transaction." + i + ".";

                long id = Long.parseLong(
                        properties.getProperty(key + "id")
                );

                String amountText =
                        properties.getProperty(key + "amount");
                String balanceText =
                        properties.getProperty(key + "resultingBalance");

                BigDecimal amount = amountText.isEmpty()
                        ? null
                        : new BigDecimal(amountText);

                BigDecimal resultingBalance = balanceText.isEmpty()
                        ? null
                        : new BigDecimal(balanceText);

                loadedTransactions.add(new Transaction(
                        id,
                        properties.getProperty(key + "dateTime"),
                        properties.getProperty(key + "type"),
                        amount,
                        resultingBalance,
                        properties.getProperty(key + "note", "")
                ));

                highestId = Math.max(highestId, id);
            }

            balance = loadedBalance;
            transactions.clear();
            transactions.addAll(loadedTransactions);
            nextTransactionId = highestId + 1;

        } catch (NumberFormatException | NullPointerException e) {
            throw new IOException("The saved account data is invalid.", e);
        }
    }

    private void loadOldFormat(List<String> lines) throws IOException {
        try {
            BigDecimal oldBalance = new BigDecimal(lines.get(0))
                    .setScale(2, RoundingMode.UNNECESSARY);

            if (oldBalance.signum() < 0) {
                throw new IOException("The saved balance is invalid.");
            }

            balance = oldBalance;
            transactions.clear();
            nextTransactionId = 1;

            for (int i = 1; i < lines.size(); i++) {
                transactions.add(new Transaction(
                        nextTransactionId++,
                        "Unknown (older version)",
                        "LEGACY",
                        null,
                        null,
                        lines.get(i)
                ));
            }
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IOException("The older account file is invalid.", e);
        }
    }
}