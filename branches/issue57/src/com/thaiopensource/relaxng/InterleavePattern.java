package com.thaiopensource.relaxng;

import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

class InterleavePattern extends BinaryPattern {
  InterleavePattern(Pattern p1, Pattern p2) {
    super(p1.isNullable() && p2.isNullable(),
	  combineHashCode(INTERLEAVE_HASH_CODE, p1.hashCode(), p2.hashCode()),
	  p1,
	  p2);
  }
  Pattern expand(PatternBuilder b) {
    Pattern ep1 = p1.expand(b);
    Pattern ep2 = p2.expand(b);
    if (ep1 != p1 || ep2 != p2)
      return b.makeInterleave(ep1, ep2);
    else
      return this;
  }
  Pattern residual(PatternBuilder b, Atom a) {
    return b.makeChoice(b.makeInterleave(b.memoizedResidual(p1, a), p2),
			b.makeInterleave(p1, b.memoizedResidual(p2, a)));
  }

  void initialContentPatterns(String namespaceURI, String localName, PatternSet ts) {
    p1.initialContentPatterns(namespaceURI, localName, ts);
    p2.initialContentPatterns(namespaceURI, localName, ts);
  }

  PatternPair unambigContentPattern(PatternBuilder b,
			      String namespaceURI,
			      String localName) {
    PatternPair cp1 = b.memoizedUnambigContentPattern(p1, namespaceURI, localName);
    if (cp1 == null)
      return null;
    PatternPair cp2 = b.memoizedUnambigContentPattern(p2, namespaceURI, localName);
    if (cp2 == null)
      return null;
    if (cp1.isEmpty()) {
      if (cp2.isEmpty())
	return cp1;
      return new PatternPair(cp2.getContentPattern(),
			  b.makeInterleave(p1, cp2.getResidualPattern()));
    }
    else if (cp2.isEmpty())
      return new PatternPair(cp1.getContentPattern(),
			  b.makeInterleave(cp1.getResidualPattern(), p2));
    else if (cp1.getContentPattern() == cp2.getContentPattern())
      return new PatternPair(cp1.getContentPattern(),
			  b.makeChoice(b.makeInterleave(cp1.getResidualPattern(),
							p2),
				       b.makeInterleave(p1,
							cp2.getResidualPattern())));
    else
      return null;
  }

  Pattern combinedInitialContentPattern(PatternBuilder b,
				  String namespaceURI,
				  String localName,
                                  int recoveryLevel) {
    return b.makeChoice(p1.combinedInitialContentPattern(b,
						      namespaceURI,
						      localName,
                                                      recoveryLevel),
			p2.combinedInitialContentPattern(b,
						      namespaceURI,
						      localName,
                                                      recoveryLevel));
  }

  Pattern endAttributes(PatternBuilder b, boolean recovering) {
    Pattern cp1 = b.memoizedEndAttributes(p1, recovering);
    Pattern cp2 = b.memoizedEndAttributes(p2, recovering);
    if (cp1 == p1 && cp2 == p2)
      return this;
    return b.makeInterleave(cp1, cp2);
  }

  void checkRestrictions(int context, DuplicateAttributeDetector dad) throws RestrictionViolationException {
    switch (context) {
    case START_CONTEXT:
      throw new RestrictionViolationException("start_contains_interleave");
    case DATA_EXCEPT_CONTEXT:
      throw new RestrictionViolationException("data_except_contains_interleave");
    }
    super.checkRestrictions(context == ELEMENT_REPEAT_CONTEXT
			    ? ELEMENT_REPEAT_INTERLEAVE_CONTEXT
			    : context,
			    dad);
    if (context != LIST_CONTEXT
	&& !contentTypeGroupable(p1.getContentType(), p2.getContentType()))
      throw new RestrictionViolationException("interleave_string");
  }

  void accept(PatternVisitor visitor) {
    visitor.visitInterleave(p1, p2);
  }
}