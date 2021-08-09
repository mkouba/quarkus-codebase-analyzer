package com.github.mkouba;

import java.util.List;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@CheckedTemplate
public class Templates {

    static native TemplateInstance report(List<Result> results);

}
