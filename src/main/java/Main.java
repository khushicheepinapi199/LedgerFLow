import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        Path dataFile = Path.of("account-data.txt");
        Path friendDataFile = Path.of("account-AC1002.txt");
        Path pinFile = Path.of("pin.txt");

        String savedPin;

        try {
            if (Files.exists(pinFile)) {
                savedPin = Files.readString(pinFile).trim();

                if (!savedPin.matches("\\d{4}")) {
                    System.out.println("The saved PIN file is invalid.");
                    scanner.close();
                    return;
                }
            } else {
                while (true) {
                    System.out.print("Create a 4-digit PIN: ");
                    String newPin = scanner.nextLine().trim();

                    if (!newPin.matches("\\d{4}")) {
                        System.out.println(
                                "Your PIN must contain exactly 4 digits."
                        );
                        continue;
                    }

                    System.out.print("Confirm your PIN: ");
                    String confirmation = scanner.nextLine().trim();

                    if (!newPin.equals(confirmation)) {
                        System.out.println(
                                "PINs do not match. Please try again."
                        );
                        continue;
                    }

                    Files.writeString(pinFile, newPin);
                    savedPin = newPin;
                    System.out.println("PIN created successfully.");
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println(
                    "Could not read or save the PIN: " + e.getMessage()
            );
            scanner.close();
            return;
        }

        Account account = new Account(
                "Khushi", "AC1001", savedPin, new BigDecimal("100.00")
        );

        // This account receives transfers. It has no login in this demo.
        Account friend = new Account(
                "Friend", "AC1002", "", new BigDecimal("0.00")
        );

        try {
            account.loadFromFile(dataFile);
            friend.loadFromFile(friendDataFile);
        } catch (IOException e) {
            System.out.println(
                    "Could not load account data: " + e.getMessage()
            );
            scanner.close();
            return;
        }

        boolean loggedIn = false;

        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("Enter your PIN: ");
            String enteredPin = scanner.nextLine().trim();

            if (account.verifyPin(enteredPin)) {
                loggedIn = true;
                break;
            }

            System.out.println("Incorrect PIN.");
        }

        if (!loggedIn) {
            System.out.println("Too many incorrect attempts. Goodbye!");
            scanner.close();
            return;
        }

        System.out.println("Welcome, " + account.getAccountHolder() + "!");

        boolean running = true;

        while (running) {
            System.out.println("\n--- Bank Account Menu ---");
            System.out.println("1. Check balance");
            System.out.println("2. Deposit");
            System.out.println("3. Withdraw");
            System.out.println("4. View account details");
            System.out.println("5. View transaction history");
            System.out.println("6. Transfer to Friend (AC1002)");
            System.out.println("7. Change PIN");
            System.out.println("8. Exit");
            System.out.print("Choose an option: ");

            int choice;

            try {
                choice = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a number from 1 to 8.");
                continue;
            }

            switch (choice) {
                case 1:
                    System.out.println(
                            "Balance: $" + account.getBalance()
                    );
                    break;

                case 2:
                    System.out.print("Enter amount to deposit: $");
                    BigDecimal depositAmount = readAmount(scanner);

                    if (account.deposit(depositAmount)) {
                        System.out.println("Deposit successful.");
                        saveAccount(account, dataFile);
                    } else {
                        System.out.println(
                                "Enter a positive amount with no more "
                                        + "than 2 decimal places."
                        );
                    }
                    break;

                case 3:
                    System.out.print("Enter amount to withdraw: $");
                    BigDecimal withdrawalAmount = readAmount(scanner);

                    if (account.withdraw(withdrawalAmount)) {
                        System.out.println("Withdrawal successful.");
                        saveAccount(account, dataFile);
                    } else {
                        System.out.println(
                                "Invalid amount or insufficient balance."
                        );
                    }
                    break;

                case 4:
                    System.out.println(
                            "Name: " + account.getAccountHolder()
                    );
                    System.out.println(
                            "Account number: " + account.getAccountNumber()
                    );
                    break;

                case 5:
                    System.out.println("--- Transaction History ---");

                    for (Account.Transaction transaction
                            : account.getTransactionHistory()) {

                        String amount = transaction.amount() == null
                                ? "unknown"
                                : "$" + transaction.amount();

                        String resultingBalance =
                                transaction.resultingBalance() == null
                                        ? "unknown"
                                        : "$" + transaction.resultingBalance();

                        System.out.printf(
                                "ID: %d | %s | %s | Amount: %s"
                                        + " | Balance: %s%n",
                                transaction.id(),
                                transaction.dateTime(),
                                transaction.type(),
                                amount,
                                resultingBalance
                        );

                        if (!transaction.note().isBlank()) {
                            System.out.println(
                                    "  Details: " + transaction.note()
                            );
                        }
                    }
                    break;

                case 6:
                    System.out.print(
                            "Enter amount to transfer to AC1002: $"
                    );
                    BigDecimal transferAmount = readAmount(scanner);

                    if (account.transferTo(friend, transferAmount)) {
                        saveAccount(account, dataFile);
                        saveAccount(friend, friendDataFile);

                        System.out.println("Transfer successful.");
                        System.out.println(
                                "Your balance: $" + account.getBalance()
                        );
                        System.out.println(
                                "Friend's balance: $" + friend.getBalance()
                        );
                    } else {
                        System.out.println(
                                "Invalid amount or insufficient balance."
                        );
                    }
                    break;

                case 7:
                    System.out.print("Enter your current PIN: ");
                    String currentPin = scanner.nextLine().trim();

                    if (!account.verifyPin(currentPin)) {
                        System.out.println("Incorrect current PIN.");
                        break;
                    }

                    System.out.print("Enter a new 4-digit PIN: ");
                    String newPin = scanner.nextLine().trim();

                    if (!newPin.matches("\\d{4}")) {
                        System.out.println(
                                "The new PIN must contain exactly 4 digits."
                        );
                        break;
                    }

                    if (newPin.equals(currentPin)) {
                        System.out.println(
                                "Choose a different PIN."
                        );
                        break;
                    }

                    System.out.print("Confirm your new PIN: ");
                    String confirmation = scanner.nextLine().trim();

                    if (!newPin.equals(confirmation)) {
                        System.out.println(
                                "PINs do not match. PIN was not changed."
                        );
                        break;
                    }

                    try {
                        if (account.changePin(
                                currentPin, newPin, pinFile)) {
                            System.out.println(
                                    "PIN changed successfully."
                            );
                        }
                    } catch (IOException e) {
                        System.out.println(
                                "Could not save the new PIN: "
                                        + e.getMessage()
                        );
                    }
                    break;

                case 8:
                    saveAccount(account, dataFile);
                    saveAccount(friend, friendDataFile);
                    System.out.println("Goodbye!");
                    running = false;
                    break;

                default:
                    System.out.println(
                            "Please choose a number from 1 to 8."
                    );
            }
        }

        scanner.close();
    }

    private static BigDecimal readAmount(Scanner scanner) {
        try {
            return new BigDecimal(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void saveAccount(Account account, Path file) {
        try {
            account.saveToFile(file);
        } catch (IOException e) {
            System.out.println(
                    "Warning: Could not save account data: "
                            + e.getMessage()
            );
        }
    }
}