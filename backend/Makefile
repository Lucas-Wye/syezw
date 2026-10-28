build:
	cargo build

release:
	cargo build --release

test:
	cargo test -- --test-threads=1

check:
	cargo check
	cargo clippy -- -D warnings
	cargo fmt -- --check

open:
	sqlite_web syezw.db

decrypt:
	cargo run \
		--bin decrypt_db -- \
		--db ./syezw.db \
		--passphrase 'your app AES passphrase' \
		--api-key 'your API key' \
		--output ./syezw.decrypted.sqlite \
		--images-dir ./syezw_images
