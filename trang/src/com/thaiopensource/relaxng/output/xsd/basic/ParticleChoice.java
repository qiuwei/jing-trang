package com.thaiopensource.relaxng.output.xsd.basic;

import com.thaiopensource.relaxng.edit.SourceLocation;

import java.util.List;
import java.util.Collections;

public class ParticleChoice extends Particle {
  private final List children;

  public ParticleChoice(SourceLocation location, List children) {
    super(location);
    this.children = Collections.unmodifiableList(children);
  }

  public List getChildren() {
    return children;
  }

  public Object accept(ParticleVisitor visitor) {
    return visitor.visitChoice(this);
  }
}