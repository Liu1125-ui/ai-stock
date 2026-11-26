package com.aistock.notification.alertmail;
/** Only invalid fixed evidence or ciphertext; never a database availability error. */
final class AlertMailPayloadException extends IllegalStateException {
 AlertMailPayloadException(){super("Alert mail payload invalid");}
}
