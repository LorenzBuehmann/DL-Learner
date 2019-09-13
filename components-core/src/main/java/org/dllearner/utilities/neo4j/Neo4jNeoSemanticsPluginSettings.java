package org.dllearner.utilities.neo4j;

/**
 * Contains the settings around the Neosemantic plugin for Neo4J.
 *
 * @author Lorenz Buehmann
 */
public class Neo4jNeoSemanticsPluginSettings {

    private static String DEFAULT_RESOURCE_LABEL = "Resource";
    private static String DEFAULT_CLASS_LABEL = "Class";
    private static String DEFAULT_OBJECT_PROPERTY_LABEL = "Relationship";
    private static String DEFAULT_DATA_PROPERTY_LABEL = "Property";
    private static String DEFAULT_SUBCLASSOF_REL = "SCO";
    private static String DEFAULT_SUBPROPERTYOF_REL = "SPO";
    private static String DEFAULT_DOMAIN_REL = "DOMAIN";
    private static String DEFAULT_RANGE_REL = "RANGE";

    public static final Neo4jNeoSemanticsPluginSettings STANDARD = new Neo4jNeoSemanticsPluginSettings.Builder().build();

    private String resourceLabel;
    private String classLabel;
    private String objectPropertyLabel;
    private String dataPropertyLabel;
    private String subClassOfRel;
    private String subPropertyOfRel;
    private String domainRel;
    private String rangeRel;

    private boolean keepCustomDatatypes = false;

    private Neo4jNeoSemanticsPluginSettings(String resourceLabel,
                                            String classLabel,
                                            String objectPropertyLabel,
                                            String dataPropertyLabel,
                                            String subClassOfRel, String subPropertyOfRel,
                                            String domainRel, String rangeRel,
                                            boolean keepCustomDatatypes) {
        this.resourceLabel = resourceLabel;
        this.classLabel = classLabel;
        this.objectPropertyLabel = objectPropertyLabel;
        this.dataPropertyLabel = dataPropertyLabel;
        this.subClassOfRel = subClassOfRel;
        this.subPropertyOfRel = subPropertyOfRel;
        this.domainRel = domainRel;
        this.rangeRel = rangeRel;
        this.keepCustomDatatypes = keepCustomDatatypes;
    }

    public String getResourceLabel() {
        return resourceLabel;
    }

    public String getClassLabel() {
        return classLabel;
    }

    public String getObjectPropertyLabel() {
        return objectPropertyLabel;
    }

    public String getDataPropertyLabel() {
        return dataPropertyLabel;
    }

    public String getSubClassOfRel() {
        return subClassOfRel;
    }

    public String getSubPropertyOfRel() {
        return subPropertyOfRel;
    }

    public String getDomainRel() {
        return domainRel;
    }

    public String getRangeRel() {
        return rangeRel;
    }

    public boolean isKeepCustomDatatypes() {
        return keepCustomDatatypes;
    }

    public static class Builder {

        private String resourceLabel = Neo4jNeoSemanticsPluginSettings.DEFAULT_RESOURCE_LABEL;
        private String classLabel = Neo4jNeoSemanticsPluginSettings.DEFAULT_CLASS_LABEL;
        private String objectPropertyLabel = Neo4jNeoSemanticsPluginSettings.DEFAULT_OBJECT_PROPERTY_LABEL;
        private String dataPropertyLabel = Neo4jNeoSemanticsPluginSettings.DEFAULT_DATA_PROPERTY_LABEL;
        private String subClassOfRel = Neo4jNeoSemanticsPluginSettings.DEFAULT_SUBCLASSOF_REL;
        private String subPropertyOfRel = Neo4jNeoSemanticsPluginSettings.DEFAULT_SUBPROPERTYOF_REL;
        private String domainRel = Neo4jNeoSemanticsPluginSettings.DEFAULT_DOMAIN_REL;
        private String rangeRel = Neo4jNeoSemanticsPluginSettings.DEFAULT_RANGE_REL;

        private boolean keepCustomDatatypes = false;

        public Neo4jNeoSemanticsPluginSettings.Builder setResourceLabel(String resourceLabel) {
            this.resourceLabel = resourceLabel;
            return this;
        }

        public Neo4jNeoSemanticsPluginSettings.Builder setClassLabel(String classLabel) {
            this.classLabel = classLabel;
            return this;
        }

        public Neo4jNeoSemanticsPluginSettings.Builder setObjectPropertyLabel(String objectPropertyLabel) {
            this.objectPropertyLabel = objectPropertyLabel;
            return this;
        }

        public Neo4jNeoSemanticsPluginSettings.Builder setDataPropertyLabel(String dataPropertyLabel) {
            this.dataPropertyLabel = dataPropertyLabel;
            return this;
        }

        public Neo4jNeoSemanticsPluginSettings.Builder setSubClassOfRel(String subClassOfRel) {
            this.subClassOfRel = subClassOfRel;
            return this;
        }

        public Neo4jNeoSemanticsPluginSettings.Builder setSubPropertyOfRel(String subPropertyOfRel) {
            this.subPropertyOfRel = subPropertyOfRel;
            return this;
        }

        public Neo4jNeoSemanticsPluginSettings.Builder setDomainRel(String domainRel) {
            this.domainRel = domainRel;
            return this;
        }

        public Neo4jNeoSemanticsPluginSettings.Builder setRangeRel(String rangeRel) {
            this.rangeRel = rangeRel;
            return this;
        }

        public Neo4jNeoSemanticsPluginSettings.Builder setKeepCustomDatatypes(boolean keepCustomDatatypes) {
            this.keepCustomDatatypes = keepCustomDatatypes;
            return this;
        }

        Neo4jNeoSemanticsPluginSettings build() {
            return new Neo4jNeoSemanticsPluginSettings(resourceLabel, classLabel, objectPropertyLabel, dataPropertyLabel, subClassOfRel, subPropertyOfRel, domainRel, rangeRel, keepCustomDatatypes);
        }
    }


}