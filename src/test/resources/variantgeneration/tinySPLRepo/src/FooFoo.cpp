class FooFoo {
  void bla() {
    true_1();
#if A
    A_1();
#if B
    AB_1();
#endif
    A_2();
    A_3();
#endif
    true_2();
  }

  void beforeInteraction() {}
#if  (C && D) || E
  void withinInteraction() {}
#endif
  void afterInteraction() {}
};