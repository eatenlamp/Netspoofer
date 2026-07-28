# this is my makefile for automating the release pls dont judge
.SHELLFLAGS = -e -o pipefail -c
RELEASE_TAG ?= $(shell date +'v%Y%m%d-%H%M%S')

release:
	@echo "🚀 Ahoy, eatenlamp! I'm Torch, and I'm here to make the apk for you!"
	@if [ -n "$$(git status --porcelain)" ]; then \
		echo "❌ Captain! There are uncommitted changes. Commit them first!"; \
		exit 1; \
	fi
	@if git rev-parse $(RELEASE_TAG) >/dev/null 2>&1; then \
		echo "❌ Captain! Tag $(RELEASE_TAG) already exists locally!"; \
		exit 1; \
	fi
	@if ! git ls-remote --exit-code origin >/dev/null 2>&1; then \
		echo "❌ Captain! Can't reach GitHub. Check your internet and SSH keys!"; \
		exit 1; \
	fi
	@echo "🔨 Building APK..."
	./gradlew clean assembleRelease
	@echo "✅ The APK is done captain! Good to go!"
	@echo "🏷️ Torch is making a tag $(RELEASE_TAG)..."
	git tag -a $(RELEASE_TAG) -m "Automatic release made by Torch (Makefile system)"
	@echo "⬆️ GitHub tag pushing..."
	git push origin $(RELEASE_TAG)
	@echo "✅ Done sir! GitHub Actions is working hard and pushing the APK release!"
	@echo "🔗 The link is here: https://github.com/eatenlamp/Netspoofer/releases"

.PHONY: release
