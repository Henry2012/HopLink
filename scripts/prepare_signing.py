"""Create a private local signing identity once, without printing credentials."""
import os
from pathlib import Path
import secrets
import subprocess

root = Path(__file__).resolve().parent.parent
key = root / '.tools/signing.jks'
properties = root / '.tools/signing.properties'
if not key.exists():
    if properties.exists():
        raise SystemExit('Signing metadata exists but key is missing; restore the key before updating an existing app.')
    password = secrets.token_urlsafe(32)
    env = dict(os.environ, SIMPLE_NODE_STORE_PASS=password)
    subprocess.run([
        '/opt/homebrew/opt/openjdk@17/bin/keytool', '-genkeypair', '-noprompt',
        '-alias', 'simplenode', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
        '-keystore', str(key), '-storepass:env', 'SIMPLE_NODE_STORE_PASS',
        '-keypass:env', 'SIMPLE_NODE_STORE_PASS', '-dname', 'CN=SimpleNode Local Build',
    ], env=env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    key.chmod(0o600)
    with open(properties, 'w', opener=lambda path, flags: os.open(path, flags, 0o600)) as f:
        f.write('password=' + password + '\n')
if not properties.exists():
    raise SystemExit('Signing properties missing; restore private local signing metadata.')
print('Local signing identity ready (private, excluded from source delivery).')
