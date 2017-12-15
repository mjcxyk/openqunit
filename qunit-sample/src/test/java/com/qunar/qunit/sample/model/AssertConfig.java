package com.qunar.qunit.sample.model;

import com.qunar.base.qunit.annotation.Element;
import com.qunar.base.qunit.model.KeyValueStore;

import java.util.List;

/**
 * User: zhaohuiyu
 * Date: 7/2/12
 * Time: 6:35 PM
 */
public class AssertConfig {
    @Element
    List<KeyValueStore> params;

    public List<KeyValueStore> getParams() {
        return params;
    }
}
