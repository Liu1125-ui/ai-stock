"""Check configured SMTP TLS/authentication without sending any email."""
from pathlib import Path
import smtplib
import ssl

def main():
    config = {}
    for line in (Path(__file__).resolve().parent.parent / '.env').read_text(encoding='utf-8-sig').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            config[key] = value
    required = ('SMTP_HOST', 'SMTP_PORT', 'SMTP_USERNAME', 'SMTP_PASSWORD')
    if not all(config.get(key) for key in required):
        print('SMTP_NOT_CONFIGURED'); return 1
    try:
        context = ssl.create_default_context()
        if config.get('SMTP_SSL_ENABLED', 'true') == 'true':
            connection = smtplib.SMTP_SSL(config['SMTP_HOST'], int(config['SMTP_PORT']), timeout=10, context=context)
        else:
            connection = smtplib.SMTP(config['SMTP_HOST'], int(config['SMTP_PORT']), timeout=10)
            connection.starttls(context=context)
        with connection:
            connection.login(config['SMTP_USERNAME'], config['SMTP_PASSWORD'])
        print('SMTP_TLS_AUTH_OK; no email sent'); return 0
    except Exception as error:
        print('SMTP_CHECK_FAILED; category=' + type(error).__name__); return 1
    finally:
        config.clear()

if __name__ == '__main__':
    raise SystemExit(main())
