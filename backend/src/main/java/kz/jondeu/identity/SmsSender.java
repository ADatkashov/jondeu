package kz.jondeu.identity;

/** SMS delivery abstraction. Production must provide a real gateway implementation. */
public interface SmsSender {
    void send(String phone, String text);
}
