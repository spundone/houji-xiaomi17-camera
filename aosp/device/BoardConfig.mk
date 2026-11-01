#
# SPDX-FileCopyrightText: 2026 The houji-xiaomi17-camera authors
# SPDX-License-Identifier: Apache-2.0
#

XCAM17_PATH := device/xiaomi/houji-miuicamera-68

# Properties
TARGET_SYSTEM_PROP += $(XCAM17_PATH)/system.prop
TARGET_PRODUCT_PROP += $(XCAM17_PATH)/product.prop

# Sepolicy
BOARD_VENDOR_SEPOLICY_DIRS += $(XCAM17_PATH)/sepolicy/vendor
