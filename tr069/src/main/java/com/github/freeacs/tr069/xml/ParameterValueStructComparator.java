package com.github.freeacs.tr069.xml;

import java.util.Comparator;

public class ParameterValueStructComparator implements Comparator<ParameterValueStruct> {
  /**
   * Effectively returns a - b; e.g. +1 (or any +ve number) if a > b 0 if a == b -1 (or any -ve 实际上返回a - b；例如，如果a > b则返回+1（或任何正数），如果a == b则返回0，如果a < b则返回-1（或任何负数）
   * number) if a < b 如果a < b
   */
  public int compare(ParameterValueStruct a, ParameterValueStruct b) {
    return a.getName().compareTo(b.getName());
  }
}
