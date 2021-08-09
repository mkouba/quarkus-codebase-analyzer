package com.github.mkouba;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class Result implements Comparable<Result> {

    private QuarkusVersion version;
    private Ref tag;
    private long javaSourceFiles;
    private long javaTypes;
    private long buildItems;
    private long configItems;

    public Ref getTag() {
        return tag;
    }

    public void setTag(Ref tag) {
        this.tag = tag;
        this.version = new QuarkusVersion(getTagName());
    }

    public String getTagName() {
        // refs/tags/2.1.1.Final -> 2.1.1.Final
        return Repository.shortenRefName(tag.getName());
    }

    public long getJavaSourceFiles() {
        return javaSourceFiles;
    }

    public void setJavaSourceFiles(long javaSourceFiles) {
        this.javaSourceFiles = javaSourceFiles;
    }
    
    public long getJavaTypes() {
        return javaTypes;
    }

    public void setJavaTypes(long javaTypes) {
        this.javaTypes = javaTypes;
    }

    public QuarkusVersion getVersion() {
        return version;
    }
    
    public long getBuildItems() {
        return buildItems;
    }

    public void setBuildItems(long buildItems) {
        this.buildItems = buildItems;
    }
    
    public long getConfigItems() {
        return configItems;
    }

    public void setConfigItems(long configItems) {
        this.configItems = configItems;
    }

    @Override
    public String toString() {
        return "Result [tag=" + getTagName() + "]";
    }

    @Override
    public int compareTo(Result o) {
        return version.compareTo(o.version);
    }

}
