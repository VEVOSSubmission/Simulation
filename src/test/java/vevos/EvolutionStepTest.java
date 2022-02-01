package vevos;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import vevos.functjonal.CachedValue;
import vevos.repository.Commit;
import vevos.util.StackUtil;
import vevos.variability.EvolutionStep;
import vevos.variability.sequenceextraction.CleaningEvolutionStepsStream;

import java.util.*;

public class EvolutionStepTest {
    private static class TestCommit extends Commit implements CachedValue {
        private boolean forgot = false;
        public TestCommit(final String commitId) {
            super(commitId);
        }
        @Override
        public void forget() {
            forgot = true;
            System.out.println("forget(" + super.id() + ")");
        }

        public boolean forgotten() {
            return forgot;
        }
    }

    private static TestCommit commit(final int id) {
        return new TestCommit("" + id);
    }

    private static EvolutionStep<TestCommit> step(final TestCommit parent, final TestCommit child) {
        return new EvolutionStep<>(parent, child);
    }

    private static EvolutionStep<TestCommit> step(final Map<Integer, TestCommit> commits, final int parent, final int child) {
        return step(commits.get(parent), commits.get(child));
    }

    private Map<Integer, TestCommit> successCommits;
    // valid pairs of commits
    private EvolutionStep<TestCommit>
            s12,
            s23,
            s34,
            s45,
            s1011,
            s1112,
            s1213;

    @Before
    public void generateInput() {
        successCommits = new HashMap<>();
        for (int i = 1; i <= 5; ++i) {
            successCommits.put(i, commit(i));
        }
        for (int i = 10; i <= 13; ++i) {
            successCommits.put(i, commit(i));
        }

        s12 = step(successCommits, 1, 2);
        s23 = step(successCommits, 2, 3);
        s34 = step(successCommits, 3, 4);
        s45 = step(successCommits, 4, 5);
        s1011 = step(successCommits, 10, 11);
        s1112 = step(successCommits, 11, 12);
        s1213 = step(successCommits, 12, 13);
    }

    @Test
    public void testMergeStacks() {
        final Stack<Integer> b = new Stack<>();
        b.push(1);
        b.push(2);
        final Stack<Integer> a = new Stack<>();
        a.push(3);
        a.push(4);
        final Stack<Integer> result = new Stack<>();
        result.push(1);
        result.push(2);
        result.push(3);
        result.push(4);

        StackUtil.pushAToB(a, b);
        Assert.assertEquals(b, result);
    }

    @Test
    public void evolSteps() {
        // random order of commit pairs
        final List<EvolutionStep<TestCommit>> input = List.of(
                s12,
                s45,
                s23,
                s34,

                s1213,
                s1011,
                s1112
        );

        // we expect two ordered sublists (in reverse order)
        final List<List<EvolutionStep<TestCommit>>> expectedOutputSequences = List.of(
                new ArrayList<>(List.of(s45, s34, s23, s12)),
                new ArrayList<>(List.of(s1213, s1112, s1011))
        );

        final CleaningEvolutionStepsStream<TestCommit> steps = new CleaningEvolutionStepsStream<>(input);
        System.out.println("Sorted steps: " + steps);

        List<EvolutionStep<TestCommit>> currentExpectedOutput = null;
        for (final EvolutionStep<TestCommit> step : steps) {
//            System.out.println("Processing " + step);

            if (currentExpectedOutput == null) {
                for (final List<EvolutionStep<TestCommit>> expectedChain : expectedOutputSequences) {
                    if (!expectedChain.isEmpty() && step.equals(expectedChain.get(0))) {
                        currentExpectedOutput = expectedChain;
                        break;
                    }
                }
                if (currentExpectedOutput == null) {
                    Assert.fail("Found step " + step + " is not the start of any expected output sequence " + expectedOutputSequences + "!");
                }
            }

            Assert.assertEquals(step, currentExpectedOutput.get(0));
            currentExpectedOutput.remove(0);
            if (currentExpectedOutput.isEmpty()) {
                currentExpectedOutput = null;
            }
        }

        for (final TestCommit c : successCommits.values()) {
            Assert.assertTrue("Commit " + c + " is still cached!", c.forgotten());
        }
    }
}
