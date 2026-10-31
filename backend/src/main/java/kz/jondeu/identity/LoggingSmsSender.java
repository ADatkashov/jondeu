package kz.jondeu.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/** Development-only sender: writes the message to the log. NOT a real SMS gateway. */
@Component
@ConditionalOnMissingBean(value = SmsSender.class, ignored = LoggingSmsSender.class)
public class LoggingSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public void send(String phone, String text) {
        log.info("[DEV-SMS] to={} text={}", phone, text);
    }
}
