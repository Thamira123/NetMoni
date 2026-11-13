package com.netmoni.server.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import com.netmoni.server.dto.alert.AlertResponse;
@Component
@ConditionalOnProperty(name="netmoni.notifications.email.enabled",havingValue="true")
public class AlertEmailNotifier {
	private static final Logger log=LoggerFactory.getLogger(AlertEmailNotifier.class);
	private final JavaMailSender sender; private final String recipient; private final String from;
	public AlertEmailNotifier(JavaMailSender sender,
			@org.springframework.beans.factory.annotation.Value("${netmoni.notifications.email.recipient}") String recipient,
			@org.springframework.beans.factory.annotation.Value("${spring.mail.username:netmoni@localhost}") String from){this.sender=sender;this.recipient=recipient;this.from=from;}
	public void sendCritical(AlertResponse alert){try{SimpleMailMessage m=new SimpleMailMessage();m.setFrom(from);m.setTo(recipient);m.setSubject("NetMoni CRITICAL alert: "+alert.type());m.setText(alert.message());sender.send(m);}catch(RuntimeException e){log.warn("Could not send email for alert {}",alert.alertId(),e);}}
}
