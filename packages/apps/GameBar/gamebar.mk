#
# Copyright (C) 2025 kenway214
# SPDX-License-Identifier: Apache-2.0
#

# GameBar app
PRODUCT_PACKAGES += \
    GameBar

# GameBar init rc
PRODUCT_PACKAGES += \
    init.gamebar.rc

# Gamebar sepolicy
include device/nothing/Spacewar/packages/apps/GameBar/sepolicy/SEPolicy.mk
