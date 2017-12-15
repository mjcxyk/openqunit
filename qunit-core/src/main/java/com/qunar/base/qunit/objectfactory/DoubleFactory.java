package com.qunar.base.qunit.objectfactory;

import java.lang.reflect.Type;

public class DoubleFactory extends InstanceFactory {
    @Override
    protected Object create(Type type, Object value) {
        if (value == null) {
            return type.equals(Double.TYPE) ? 0.0 : null;
        }
        return Double.valueOf(value.toString());
    }

    @Override
    protected boolean support(Type type) {
        return type.equals(Double.class) || type.equals(Double.TYPE);
    }
}
