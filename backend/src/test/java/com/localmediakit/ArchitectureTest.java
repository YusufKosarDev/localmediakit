package com.localmediakit;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * The architectural rules this project actually relies on, as tests.
 *
 * <p>Each one encodes an invariant that was previously held by discipline
 * alone. Deliberately a short list: a rule nobody would violate is noise, and
 * noise is what makes people stop reading failures. Rules that could not be
 * stated honestly were left out rather than written with enough exemptions to
 * assert nothing — package-cycle freedom (user and billing legitimately refer
 * to each other through account deletion), billing isolation (demo, shared and
 * user all import it for good reasons), and a ban on java.util.Date (its one
 * use is required by the JWT library's API).
 *
 * <p>The package layout is vertical slices, not layers, so there is no
 * layeredArchitecture() rule here either: "controller" and "service" are roles
 * carried by class names, which is why the naming rules at the bottom are
 * load-bearing rather than cosmetic.
 *
 * <p>Classes are imported once and cached by the ArchUnit JUnit engine, so the
 * whole file costs one scan.
 */
@AnalyzeClasses(packages = "com.localmediakit", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /* ------------------------------------------------------------------ */
    /* 1. Locale-safe case folding                                        */
    /* ------------------------------------------------------------------ */

    /**
     * The rule with an actual incident behind it.
     *
     * <p>A migration used {@code UPPER('light')} and a Turkish database folded
     * it to 'LİGHT' (dotted capital I), which is not an enum constant — every
     * account that predated the migration then failed to load and answered 500.
     * The same trap exists in Java: {@code "I".toLowerCase()} is "ı" on a
     * Turkish JVM. Both call sites found when this rule was written were real
     * bugs (referrer grouping and duplicate-label detection), not false
     * positives.
     */
    @ArchTest
    static final ArchRule caseFoldingIsLocaleIndependent = noClasses()
            .should(callMethodWithoutLocale("toLowerCase"))
            .orShould(callMethodWithoutLocale("toUpperCase"))
            .because("case folding without an explicit Locale changes meaning on a Turkish JVM "
                    + "(\"I\" folds to a dotless \"i\"); pass Locale.ROOT");

    private static ArchCondition<JavaClass> callMethodWithoutLocale(String methodName) {
        return new ArchCondition<>("call String." + methodName + "() without a Locale") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaMethodCall call : item.getMethodCallsFromSelf()) {
                    boolean offending = call.getTarget().getOwner().isEquivalentTo(String.class)
                            && call.getTarget().getName().equals(methodName)
                            && call.getTarget().getRawParameterTypes().isEmpty();
                    if (offending) {
                        events.add(SimpleConditionEvent.satisfied(item, call.getDescription()));
                    }
                }
            }
        };
    }

    /* ------------------------------------------------------------------ */
    /* 2-3. The write path: ownership and transactions live in services    */
    /* ------------------------------------------------------------------ */

    /**
     * Controllers must not reach a repository.
     *
     * <p>This is a security rule, not a tidiness one: the ownership check that
     * makes an IDOR unrepresentable ({@code MediaKitAccess.requireOwnedKit},
     * which loads only through owner-scoped queries) lives in the service
     * layer. A controller talking to a repository directly would be one line
     * away from loading another user's row.
     */
    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .because("ownership checks and transactions belong to services; a controller "
                    + "reaching a repository would bypass the owner-scoped queries");

    /**
     * The same invariant seen from the other side.
     *
     * <p>MediaKitAccess is the deliberate exception: it is the shared ownership
     * guard itself, and exists precisely so services do not each re-implement
     * the owner-scoped lookup.
     *
     * <p>UnlockRateLimiter is the other one, and it is worth saying why it is
     * not a hole in the rule. What this rule protects is owner-scoped access:
     * rows that belong to a user must be reached through a service that knows
     * whose they are. The unlock counter has no owner to scope by -- it counts
     * wrong passwords against a slug and an IP, keeps them for fifteen minutes
     * and deletes them -- and it is the only class that touches that table.
     * Wrapping it in a second class named ...Service to satisfy the spelling
     * would add indirection and nothing else.
     */
    @ArchTest
    static final ArchRule repositoriesAreUsedByServicesOnly = classes()
            .that().haveSimpleNameEndingWith("Repository")
            .should().onlyHaveDependentClassesThat(
                    new DescribedPredicate<>("are services, other repositories, or a named owner of an ownerless table") {
                        @Override
                        public boolean test(JavaClass origin) {
                            String name = origin.getSimpleName();
                            return name.endsWith("Service")
                                    || name.endsWith("Repository")
                                    || name.equals("MediaKitAccess")
                                    // Startup/scheduled data maintenance, not a request path.
                                    || name.equals("DemoDataInitializer")
                                    // Owns one ownerless table: the unlock
                                    // failure counter. See the note above.
                                    || name.equals("UnlockRateLimiter");
                        }
                    })
            .because("repository access is what services exist to own");

    /* ------------------------------------------------------------------ */
    /* 4. The immutable snapshot                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Only the publication service may build a snapshot.
     *
     * <p>The whole product rests on "a published page is frozen until the owner
     * publishes again". If any other class could construct a
     * {@link com.localmediakit.mediakit.MediaKitSnapshot}, that guarantee would
     * be a convention rather than a fact. Reading one is unrestricted — the
     * public page and the version diff both do.
     */
    @ArchTest
    static final ArchRule onlyPublicationBuildsSnapshots = noClasses()
            .that().doNotHaveSimpleName("MediaKitPublicationService")
            .and().doNotHaveSimpleName("MediaKitSnapshot")
            .should().callConstructorWhere(
                    new DescribedPredicate<>("a MediaKitSnapshot is constructed") {
                        @Override
                        public boolean test(com.tngtech.archunit.core.domain.JavaConstructorCall call) {
                            return call.getTarget().getOwner().getSimpleName().equals("MediaKitSnapshot");
                        }
                    })
            .because("freezing content at publish is the one place a snapshot may be created");

    /* ------------------------------------------------------------------ */
    /* 5. The domain model stays free of HTTP                             */
    /* ------------------------------------------------------------------ */

    @ArchTest
    static final ArchRule entitiesDoNotDependOnTheWeb = noClasses()
            .that().areAnnotatedWith(jakarta.persistence.Entity.class)
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.web..", "jakarta.servlet..")
            .because("a persisted model that knows about HTTP cannot be reused outside a request");

    /* ------------------------------------------------------------------ */
    /* 6. Plan gating stays in one place                                  */
    /* ------------------------------------------------------------------ */

    /**
     * Reading a specific Plan constant is restricted to the packages that
     * legitimately decide or assign one.
     *
     * <p>PlanPolicy answers "what may this plan do"; the demo seeder is the
     * only thing left that assigns one. Anywhere else, a bare
     * {@code plan == Plan.PRO} is a gating decision taken outside the one class
     * meant to hold them all — which is how a feature gate ends up duplicated
     * and inconsistent.
     *
     * <p>Narrowed deliberately after the first draft banned the {@code Plan}
     * type outright and flagged {@code owner.getPlan().name()} in the analytics
     * response. That is not gating, it is reporting which plan the account is
     * on; the gate beside it correctly goes through
     * {@code planPolicy.detailedAnalyticsEnabled(...)}. The rule was wrong
     * there, not the code — so it now targets the enum constants, which is what
     * a hard-coded gate actually looks like.
     */
    @ArchTest
    static final ArchRule planConstantsStayInTheirPackages = noClasses()
            .that().resideOutsideOfPackages(
                    "com.localmediakit.user..",
                    "com.localmediakit.demo..")
            .should(readAPlanConstant())
            .because("plan-based gating belongs in PlanPolicy, not spread across features");

    private static ArchCondition<JavaClass> readAPlanConstant() {
        return new ArchCondition<>("read a Plan enum constant") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                item.getFieldAccessesFromSelf().stream()
                        .filter(access -> access.getTarget().getOwner()
                                .getFullName().equals("com.localmediakit.user.Plan"))
                        // Enum constants are static fields; resolving the member
                        // is what distinguishes them from an instance read.
                        .filter(access -> access.getTarget().resolveMember()
                                .map(field -> field.getModifiers().contains(JavaModifier.STATIC))
                                .orElse(false))
                        .forEach(access ->
                                events.add(SimpleConditionEvent.satisfied(item, access.getDescription())));
            }
        };
    }

    /* ------------------------------------------------------------------ */
    /* 7-8. Spring usage that fails silently when it is wrong             */
    /* ------------------------------------------------------------------ */

    /**
     * Every scheduled method holds a lock while it runs.
     *
     * <p>The jobs in this application read rows, act on the world and write the
     * result back. Two instances running one on the same tick does not fail —
     * it mails a brand notification twice, folds an analytics day twice,
     * publishes a scheduled kit twice. Nothing is logged, because from each
     * instance's side nothing went wrong.
     *
     * <p>The way that guarantee gets lost is not a bad lock. It is the eighth
     * job being added by someone who did not know locking was a thing here, and
     * there is nothing about writing {@code @Scheduled} that would tell them.
     * So the rule is stated where it cannot be missed rather than left to
     * review, and it reads the compiled classes rather than the beans a test
     * context happens to have — DemoResetJob is conditional on a property that
     * is off in tests, and a check driven by the context would silently stop
     * covering it.
     */
    @ArchTest
    static final ArchRule scheduledMethodsAreLocked = methods()
            .that().areAnnotatedWith(org.springframework.scheduling.annotation.Scheduled.class)
            .should(beLockedUnderAName())
            .because("on a second instance an unlocked scheduled job does its work twice, silently");

    private static ArchCondition<com.tngtech.archunit.core.domain.JavaMethod> beLockedUnderAName() {
        return new ArchCondition<>("be annotated with @SchedulerLock under a non-blank name") {
            @Override
            public void check(com.tngtech.archunit.core.domain.JavaMethod method, ConditionEvents events) {
                var lock = method.tryGetAnnotationOfType(
                        net.javacrumbs.shedlock.spring.annotation.SchedulerLock.class);
                if (lock.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " is @Scheduled but not @SchedulerLock"));
                    return;
                }
                if (lock.get().name().isBlank()) {
                    events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " locks under a blank name, which locks nothing"));
                }
            }
        };
    }

    @ArchTest
    static final ArchRule noFieldInjection = noFields()
            .should().beAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
            .because("constructor injection keeps dependencies explicit and services "
                    + "constructible in a plain unit test");

    /**
     * A mistake made twice in this codebase.
     *
     * <p>Spring's transaction support is proxy-based, so {@code @Transactional}
     * on a private or protected method — or on one called from inside the same
     * class — is silently ignored. Both occurrences here (a user-row purge and
     * a notification delivery) looked transactional and were not; both were
     * rewritten to use TransactionTemplate. This makes the silent case loud.
     */
    @ArchTest
    static final ArchRule transactionalOnlyOnPublicMethods = methods()
            .that().areAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
            .should().haveModifier(JavaModifier.PUBLIC)
            .because("Spring's proxy cannot intercept a non-public method, so the annotation "
                    + "would be silently ignored");

    /* ------------------------------------------------------------------ */
    /* 9. Names carry the roles the rules above are written against       */
    /* ------------------------------------------------------------------ */

    /**
     * Load-bearing, not cosmetic: rules 2 and 3 select classes by name suffix.
     * A controller written without the suffix would quietly fall outside them.
     */
    @ArchTest
    static final ArchRule controllersAreNamedController = classes()
            .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should().haveSimpleNameEndingWith("Controller")
            .because("the repository-access rules select controllers by name");

    @ArchTest
    static final ArchRule controllerNamesAreRestControllers = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().beAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .because("a class named Controller that is not one is a misleading name");

    @ArchTest
    static final ArchRule repositoriesAreSpringDataInterfaces = classes()
            .that().haveSimpleNameEndingWith("Repository")
            .should().beInterfaces()
            .andShould().beAssignableTo(org.springframework.data.repository.Repository.class)
            .because("the repository rules assume the name means a Spring Data repository");

    /** Keeps the import from silently matching nothing if packages move. */
    @ArchTest
    static final ArchRule theProjectWasActuallyScanned = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().bePublic()
            .because("if this finds no classes the rules above are vacuously true");

    /* Unused import guard for the JavaClasses type used by predicates. */
    @SuppressWarnings("unused")
    private static void typeAnchor(JavaClasses classes) {
    }
}
