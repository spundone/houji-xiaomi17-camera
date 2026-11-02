#
# SPDX-FileCopyrightText: 2026 The houji-miuicamera-port authors
# SPDX-License-Identifier: Apache-2.0
#

MIUICAMERA_PORT_PATH := device/xiaomi/houji-miuicamera-port

# Properties
TARGET_SYSTEM_PROP += $(MIUICAMERA_PORT_PATH)/system.prop
TARGET_PRODUCT_PROP += $(MIUICAMERA_PORT_PATH)/product.prop

# Sepolicy
BOARD_VENDOR_SEPOLICY_DIRS += $(MIUICAMERA_PORT_PATH)/sepolicy/vendor

# Leica Essential: the port's helper programs must be executable
ifneq ($(wildcard $(MIUICAMERA_PORT_PATH)/leica/config.fs),)
TARGET_FS_CONFIG_GEN += $(MIUICAMERA_PORT_PATH)/leica/config.fs
endif
