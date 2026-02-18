#!/bin/sh
PREVIOUS_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")

echo "### Changes in this Release"
echo ""

if [ -n "$PREVIOUS_TAG" ]; then
	git log ${PREVIOUS_TAG}..HEAD --pretty=format:"* %h - %s"
# else
	# git log --pretty=format:"* %h - %s"
fi

echo ""
