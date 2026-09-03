package com.loldraft.data.validation

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
) {
    val isSuccess: Boolean
        get() = isValid

    val isFailure: Boolean
        get() = !isValid

    companion object {
        fun success(): ValidationResult = ValidationResult(isValid = true, errors = emptyList())

        fun failure(errors: List<String>): ValidationResult = ValidationResult(isValid = false, errors = errors)

        fun failure(vararg errors: String): ValidationResult = ValidationResult(isValid = false, errors = errors.toList())
    }
}
