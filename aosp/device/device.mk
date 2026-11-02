#
# SPDX-FileCopyrightText: 2026 The houji-miuicamera-port authors
# SPDX-License-Identifier: Apache-2.0
#

MIUICAMERA_PORT_PATH := device/xiaomi/houji-miuicamera-port

# MiuiCamera 6.8.002090.2, patched by aosp/tools/patch-apk.sh; overrides the 6.4 MiuiCamera
PRODUCT_PACKAGES += \
    MiuiCamera68 \
    MiuiCameraIconOverlay

# Priv-app permissions
PRODUCT_COPY_FILES += \
    $(MIUICAMERA_PORT_PATH)/configs/privapp-permissions-miuicamera68.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/permissions/privapp-permissions-miuicamera68.xml

# Sysconfig
PRODUCT_COPY_FILES += \
    $(MIUICAMERA_PORT_PATH)/configs/miuicamera68-hiddenapi-package-allowlist.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/sysconfig/miuicamera68-hiddenapi-package-allowlist.xml \
    $(MIUICAMERA_PORT_PATH)/configs/miuicamera68-sysconfig.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/sysconfig/miuicamera68-sysconfig.xml

# Leica Essential (Essential Moment): the Leica port's files, when the tree carries them
-include $(MIUICAMERA_PORT_PATH)/leica/leica.mk

# Soong namespaces
PRODUCT_SOONG_NAMESPACES += \
    $(MIUICAMERA_PORT_PATH)
