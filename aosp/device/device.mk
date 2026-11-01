#
# SPDX-FileCopyrightText: 2026 The houji-xiaomi17-camera authors
# SPDX-License-Identifier: Apache-2.0
#

# MiuiCamera 6.8.002090.2, patched by aosp/tools/patch-apk.sh; overrides the 6.4 MiuiCamera
PRODUCT_PACKAGES += \
    MiuiCamera68 \
    MiuiCameraIconOverlay

# Priv-app permissions
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/privapp-permissions-miuicamera68.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/permissions/privapp-permissions-miuicamera68.xml

# Sysconfig
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/miuicamera68-hiddenapi-package-allowlist.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/sysconfig/miuicamera68-hiddenapi-package-allowlist.xml \
    $(LOCAL_PATH)/configs/miuicamera68-sysconfig.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/sysconfig/miuicamera68-sysconfig.xml

# Soong namespaces
PRODUCT_SOONG_NAMESPACES += \
    $(LOCAL_PATH)
