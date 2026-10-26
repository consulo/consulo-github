// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data;

import org.jetbrains.plugins.github.api.data.GithubIssueState;
import org.jspecify.annotations.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class GHPRSearchQuery {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private final List<Term<?>> myTerms;

    public GHPRSearchQuery(List<Term<?>> terms) {
        myTerms = terms;
    }

    public List<Term<?>> getTerms() {
        return myTerms;
    }

    public boolean isEmpty() {
        return myTerms.isEmpty();
    }

    @Override
    public String toString() {
        return myTerms.stream().map(Object::toString).collect(Collectors.joining(" "));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPRSearchQuery that)) {
            return false;
        }

        return myTerms.equals(that.myTerms);
    }

    @Override
    public int hashCode() {
        return myTerms.hashCode();
    }

    @SuppressWarnings("EnumEntryName")
    public enum QualifierName {
        is("is"),
        assignee("assignee"),
        author("author"),
        head("head"),
        label("label"),
        repo("repo"),
        review("review"),
        reviewRequested("review-requested"),
        reviewedBy("reviewed-by"),
        sortBy("sort"),
        state("state") {
            @Override
            public Term<?> createTerm(String value) {
                return Term.Qualifier.Enum.from(this, GithubIssueState.class, value);
            }
        },
        type("type");

        private final String myApiName;

        QualifierName(String apiName) {
            myApiName = apiName;
        }

        public String getApiName() {
            return myApiName;
        }

        public Term<?> createTerm(String value) {
            return new Term.Qualifier.Simple(this, value);
        }

        @Override
        public String toString() {
            return myApiName;
        }
    }

    /**
     * Part of search query (search term)
     */
    public abstract static class Term<T> {
        protected final T value;

        protected Term(T value) {
            this.value = value;
        }

        public abstract @Nullable String getApiValue();

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Term<?> that)) {
                return false;
            }

            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        public static class QueryPart extends Term<String> {
            public QueryPart(String value) {
                super(value);
            }

            @Override
            public String getApiValue() {
                return value;
            }

            @Override
            public String toString() {
                return value;
            }
        }

        /**
         * A group of terms joined with "OR", wrapped in parentheses.
         */
        public static class Or extends Term<List<Term<?>>> {
            public Or(List<Term<?>> terms) {
                super(terms);
            }

            @Override
            public String getApiValue() {
                return value.stream().map(Object::toString).collect(Collectors.joining(" OR "));
            }

            @Override
            public String toString() {
                return value.stream().map(Object::toString).collect(Collectors.joining(" OR ", "(", ")"));
            }
        }

        public abstract static class Qualifier<T> extends Term<T> {
            protected final QualifierName name;

            protected Qualifier(QualifierName name, T value) {
                super(value);
                this.name = name;
            }

            public String getApiName() {
                return name.getApiName();
            }

            @Override
            public String toString() {
                return name + ":" + value;
            }

            public static class Simple extends Qualifier<String> {
                private boolean myNot;

                public Simple(QualifierName name, String value) {
                    super(name, value);
                }

                @Override
                public String getApiValue() {
                    return value;
                }

                public Simple not() {
                    myNot = !myNot;
                    return this;
                }

                @Override
                public String toString() {
                    String minus = myNot ? "-" : "";
                    return minus + name + ":" + value;
                }
            }

            public static class Enum<E extends java.lang.Enum<E>> extends Qualifier<java.lang.Enum<E>> {
                public Enum(QualifierName name, E value) {
                    super(name, value);
                }

                @Override
                public String getApiValue() {
                    return value.name();
                }

                public static <E extends java.lang.Enum<E>> Term<?> from(QualifierName name, Class<E> enumClass, String value) {
                    try {
                        return new Enum<>(name, java.lang.Enum.valueOf(enumClass, value));
                    }
                    catch (IllegalArgumentException e) {
                        return new Simple(name, value);
                    }
                }
            }

            public abstract static class Date extends Qualifier<java.util.Date> {
                protected Date(QualifierName name, java.util.Date value) {
                    super(name, value);
                }

                protected String formatDate() {
                    return DATE_FORMAT.format(value);
                }

                @Override
                public String toString() {
                    return name + ":" + formatDate();
                }

                public static class Before extends Date {
                    public Before(QualifierName name, java.util.Date value) {
                        super(name, value);
                    }

                    @Override
                    public String getApiValue() {
                        return "<" + formatDate();
                    }

                    public static Term<?> from(QualifierName name, String value) {
                        java.util.Date date;
                        try {
                            date = DATE_FORMAT.parse(value);
                        }
                        catch (ParseException e) {
                            return new Simple(name, value);
                        }
                        return new Before(name, date);
                    }
                }

                public static class After extends Date {
                    public After(QualifierName name, java.util.Date value) {
                        super(name, value);
                    }

                    @Override
                    public String getApiValue() {
                        return ">" + formatDate();
                    }

                    public static Term<?> from(QualifierName name, String value) {
                        try {
                            return new After(name, DATE_FORMAT.parse(value));
                        }
                        catch (ParseException e) {
                            return new Simple(name, value);
                        }
                    }
                }
            }
        }
    }
}
