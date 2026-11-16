package com.dev.moment.common.text;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MaxGraphemesValidator implements ConstraintValidator<MaxGraphemes, CharSequence> {

    private int max;

    @Override
    public void initialize(MaxGraphemes annotation) {
        this.max = annotation.max();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        return value == null || GraphemeCounter.count(value) <= max;
    }
}
