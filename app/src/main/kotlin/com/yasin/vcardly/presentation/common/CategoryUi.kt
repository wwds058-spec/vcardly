package com.yasin.vcardly.presentation.common

import com.yasin.vcardly.R
import com.yasin.vcardly.core.common.UiText
import com.yasin.vcardly.domain.model.Category
import com.yasin.vcardly.domain.model.SystemCategory

/** System categories show a localized resource name; custom ones show the user's text. */
fun Category.displayName(): UiText = when (systemCategory) {
    SystemCategory.BUSINESS -> UiText.of(R.string.category_business)
    SystemCategory.CUSTOMER -> UiText.of(R.string.category_customer)
    SystemCategory.SUPPLIER -> UiText.of(R.string.category_supplier)
    SystemCategory.CLIENT -> UiText.of(R.string.category_client)
    SystemCategory.PARTNER -> UiText.of(R.string.category_partner)
    SystemCategory.VENDOR -> UiText.of(R.string.category_vendor)
    SystemCategory.COLLEAGUE -> UiText.of(R.string.category_colleague)
    SystemCategory.FRIEND -> UiText.of(R.string.category_friend)
    SystemCategory.OTHER -> UiText.of(R.string.category_other)
    null -> UiText.Dynamic(name)
}
