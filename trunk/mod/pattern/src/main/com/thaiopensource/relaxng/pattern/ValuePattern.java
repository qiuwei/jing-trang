package com.thaiopensource.relaxng.pattern;

import org.relaxng.datatype.Datatype;

class ValuePattern extends StringPattern {
  private final Object obj;
  private final Datatype dt;

  ValuePattern(Datatype dt, Object obj) {
    super(combineHashCode(VALUE_HASH_CODE, dt.valueHashCode(obj)));
    this.dt = dt;
    this.obj = obj;
  }

  boolean samePattern(Pattern other) {
    if (getClass() != other.getClass())
      return false;
    if (!(other instanceof ValuePattern))
      return false;
    return (dt.equals(((ValuePattern)other).dt)
	    && dt.sameValue(obj, ((ValuePattern)other).obj));
  }

  void accept(PatternVisitor visitor) {
    visitor.visitValue(dt, obj);
  }

  <T> T apply(PatternFunction<T> f) {
    return f.caseValue(this);
  }

  void checkRestrictions(int context, DuplicateAttributeDetector dad, Alphabet alpha)
    throws RestrictionViolationException {
    switch (context) {
    case START_CONTEXT:
      throw new RestrictionViolationException("start_contains_value");
    }
  }

  Datatype getDatatype() {
    return dt;
  }

  Object getValue() {
    return obj;
  }

}