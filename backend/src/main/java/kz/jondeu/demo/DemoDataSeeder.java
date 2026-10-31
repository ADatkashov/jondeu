package kz.jondeu.demo;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import kz.jondeu.calendar.CalendarService;
import kz.jondeu.estimates.EstimateItem.Kind;
import kz.jondeu.estimates.EstimateService;
import kz.jondeu.estimates.EstimateService.ItemInput;
import kz.jondeu.identity.*;
import kz.jondeu.inspections.Inspection.Result;
import kz.jondeu.inspections.InspectionService;
import kz.jondeu.masters.MasterProfile;
import kz.jondeu.masters.MasterRepositories.MasterProfileRepository;
import kz.jondeu.masters.MasterService;
import kz.jondeu.materials.MaterialService;
import kz.jondeu.media.FileStorage;
import kz.jondeu.media.Media;
import kz.jondeu.media.MediaRepository;
import kz.jondeu.messaging.MessagingService;
import kz.jondeu.payments.PaymentEnums.*;
import kz.jondeu.payments.PaymentService;
import kz.jondeu.projects.*;
import kz.jondeu.stages.StageService;
import kz.jondeu.stages.StageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * DEMO ENVIRONMENT ONLY (jondeu.demo.enabled=true). Every record is flagged demo=true and all demo accounts share the
 * password "demo1234". The seeder drives the real services, so demo data obeys exactly the same business rules as live data.
 * Never enable in production.
 */
@Component
@ConditionalOnProperty(name = "jondeu.demo.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    public static final String PASSWORD = "demo1234";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final MasterProfileRepository profiles;
    private final ProjectService projectService;
    private final EstimateService estimates;
    private final StageService stageService;
    private final PaymentService payments;
    private final MaterialService materials;
    private final InspectionService inspections;
    private final CalendarService calendar;
    private final MessagingService messaging;
    private final MasterService masters;
    private final MediaRepository mediaRepo;
    private final FileStorage storage;
    private final ProjectRepository projects;
    private final JdbcTemplate jdbc;

    public DemoDataSeeder(UserRepository users, PasswordEncoder encoder, MasterProfileRepository profiles, ProjectService projectService, EstimateService estimates, StageService stageService,
                          PaymentService payments, MaterialService materials, InspectionService inspections, CalendarService calendar, MessagingService messaging, MasterService masters,
                          MediaRepository mediaRepo, FileStorage storage, ProjectRepository projects, JdbcTemplate jdbc) {
        this.users = users; this.encoder = encoder; this.profiles = profiles; this.projectService = projectService; this.estimates = estimates; this.stageService = stageService;
        this.payments = payments; this.materials = materials; this.inspections = inspections; this.calendar = calendar; this.messaging = messaging; this.masters = masters;
        this.mediaRepo = mediaRepo; this.storage = storage; this.projects = projects; this.jdbc = jdbc;
    }

    private static AuthUser as(User u) { return new AuthUser(u.getId(), u.getRole(), u.getFullName()); }

    private User user(String phone, String name, Role role) {
        User u = new User();
        u.setPhone(phone); u.setFullName(name); u.setRole(role); u.setCity("Almaty"); u.setDemo(true); u.setLanguage("ru");
        u.setPasswordHash(encoder.encode(PASSWORD));
        return users.save(u);
    }

    private User master(String phone, String name, Role role, String spec, String category, int years, String rate, String rating, int reviews, int done, String district, String company, boolean available) {
        User u = user(phone, name, role);
        MasterProfile m = new MasterProfile();
        m.setUserId(u.getId()); m.setSpecialization(spec); m.setCategory(category); m.setExperienceYears(years); m.setHourlyRate(new BigDecimal(rate));
        m.setRating(new BigDecimal(rating)); m.setReviewsCount(reviews); m.setCompletedProjects(done); m.setVerified(true); m.setAvailable(available);
        m.setDistrict(district); m.setCompanyName(company);
        m.setBio(spec + ". Опыт " + years + " лет. Работаю по договору, даю гарантию на выполненные работы.");
        profiles.save(m);
        return u;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (users.existsByPhone("+77010000001")) { log.info("Demo data already present, skipping"); return; }
        log.warn("*** SEEDING DEMO DATA (jondeu.demo.enabled=true). Password for all demo accounts: {} ***", PASSWORD);

        User asel = user("+77010000001", "Асель Нурланова", Role.CUSTOMER);
        User daniyar = user("+77010000002", "Данияр Сериков", Role.CUSTOMER);
        User aigerim = user("+77010000003", "Айгерим Жумабекова", Role.CUSTOMER);
        User alexey = master("+77010000004", "Алексей Петров", Role.MASTER, "Электрик", "ELECTRICAL", 8, "1500", "4.90", 96, 112, "Бостандыкский р-н", null, true);
        User sergey = master("+77010000005", "Сергей Иванов", Role.MASTER, "Сантехник", "PLUMBING", 11, "1800", "4.80", 96, 140, "Ауэзовский р-н", null, true);
        User dmitry = master("+77010000006", "Дмитрий Кузнецов", Role.MASTER, "Плиточник", "TILING", 9, "2000", "4.90", 76, 98, "Медеуский р-н", null, true);
        User ivan = master("+77010000007", "Иван Соловьёв", Role.MASTER, "Маляр", "PAINTING", 6, "1400", "4.70", 64, 71, "Алмалинский р-н", null, false);
        master("+77010000008", "Ерлан Касымов", Role.MASTER, "Штукатур", "PLASTERING", 12, "1700", "4.60", 41, 77, "Турксибский р-н", null, true);
        User stroy = master("+77010000009", "Нурлан Абенов (СтройКомфорт)", Role.COMPANY_ADMIN, "Ремонт квартир под ключ", "APARTMENT_RENOVATION", 14, "3500", "4.90", 128, 163, "Бостандыкский р-н", "СтройКомфорт Алматы", true);
        User remont = master("+77010000010", "Марат Оспанов (РемонтПро KZ)", Role.CONTRACTOR, "Кухни и ванные под ключ", "KITCHEN", 10, "3200", "4.80", 87, 104, "Ауэзовский р-н", "РемонтПро KZ", true);
        User inspector = user("+77010000011", "Бауыржан Тлеуов (инспектор)", Role.INSPECTOR);
        user("+77010000099", "Администратор платформы", Role.PLATFORM_ADMIN);

        // --------- Project 1: flagship, 64 m2, in progress ---------
        var customer = as(asel);
        var company = as(stroy);
        var p1 = projectService.create(new ProjectService.ProjectInput("Квартира, ЖК Солнечный, 64 м²", "APARTMENT", 3, new BigDecimal("64"), "Алматы, мкр. Самал-2, ЖК Солнечный, кв. 45",
            "Almaty", "Капитальный ремонт 3-комнатной квартиры под ключ: демонтаж, электрика, сантехника, отделка.", "APARTMENT_RENOVATION", new BigDecimal("10000000"), new BigDecimal("14000000"), true, "NORMAL",
            LocalDate.now().minusDays(60), null), customer);
        UUID pid = p1.id();
        projectService.submit(pid, customer);
        var prop = projectService.propose(pid, new ProjectService.ProposalInput(new BigDecimal("12455000"), 75, "Сделаем ремонт за 2,5 месяца с гарантией 12 месяцев."), company);
        projectService.accept(prop.id(), customer);
        projectService.addMember(pid, new ProjectService.MemberInput("+77010000004", ProjectRole.MASTER), company);
        projectService.addMember(pid, new ProjectService.MemberInput("+77010000005", ProjectRole.MASTER), company);
        projectService.addMember(pid, new ProjectService.MemberInput("+77010000006", ProjectRole.MASTER), company);
        projectService.addMember(pid, new ProjectService.MemberInput("+77010000011", ProjectRole.INSPECTOR), customer);
        // start date is set by the plan; the plan dates derive from project.startDate
        projects.findById(pid).ifPresent(p -> { p.setStartDate(LocalDate.now().minusDays(60)); projects.save(p); });

        var est = estimates.create(pid, new EstimateService.EstimateInput(List.of(
            li(Kind.LABOR, "Демонтажные работы", "1", "компл", "800000"), li(Kind.LABOR, "Электромонтаж", "1", "компл", "2500000"),
            li(Kind.LABOR, "Сантехнические работы", "1", "компл", "1800000"), li(Kind.LABOR, "Штукатурка и выравнивание стен", "64", "м²", "25000"),
            li(Kind.LABOR, "Покраска стен и потолков", "64", "м²", "18000"), li(Kind.MATERIAL, "Черновые и отделочные материалы", "1", "компл", "3500000")),
            new BigDecimal("1103000"), BigDecimal.ZERO, BigDecimal.ZERO), company);
        estimates.submit(est.id(), company);
        estimates.approve(est.id(), customer);

        // stages: 1..6 accepted, 7 started (floors & tiles run in parallel with finishing), weighted progress = 65.0%
        var stageList = stageService.list(pid, customer);
        for (int i = 0; i < 6; i++) {
            var s = stageList.get(i);
            stageService.update(s.id(), new StageService.StageUpdate(null, null, null, null, null, null, 100, null, null, null, null), company);
            stageService.update(s.id(), new StageService.StageUpdate(null, null, null, null, null, null, null, StageStatus.COMPLETED, null, null, null), i == 3 ? as(inspector) : customer);
        }
        var s7 = stageList.get(6);
        stageService.update(s7.id(), new StageService.StageUpdate(null, null, null, null, null, null, null, null, null, null, true), company);
        stageService.update(s7.id(), new StageService.StageUpdate(null, null, null, null, null, null, 8, null, null, alexey.getId(), null), company);
        stageService.comment(stageList.get(5).id(), "Покраска закончена, ждём приёмки цвета в спальне.", company);

        pay(pid, "Аванс по договору", Type.ADVANCE, Method.BANK_TRANSFER, "3500000", customer, company);
        pay(pid, "Демонтаж и черновые работы", Type.STAGE, Method.CASH, "2000000", customer, company);
        pay(pid, "Электрика и сантехника", Type.STAGE, Method.BANK_TRANSFER, "1500000", customer, company);
        pay(pid, "Штукатурка", Type.STAGE, Method.BANK_TRANSFER, "1753000", customer, company);
        payments.requestPayment(pid, new PaymentService.RequestCommand(Type.STAGE, new BigDecimal("1200000"), s7.id(), "Полы и плитка: предоплата"), company);

        var mt = materials.create(pid, new MaterialService.MaterialInput("Кабель ВВГнг 3×2,5", "м", new BigDecimal("600"), new BigDecimal("620"), "Электро-Алматы", LocalDate.now().minusDays(40)), company);
        materials.order(mt.id(), company); materials.receive(mt.id(), new MaterialService.ReceiveInput(new BigDecimal("600"), null, LocalDate.now().minusDays(38)), company);
        mt = materials.create(pid, new MaterialService.MaterialInput("Гипсовая штукатурка 30 кг", "меш", new BigDecimal("180"), new BigDecimal("2450"), "СтройБаза", LocalDate.now().minusDays(30)), company);
        materials.order(mt.id(), company); materials.receive(mt.id(), new MaterialService.ReceiveInput(new BigDecimal("180"), new BigDecimal("2500"), null), company);
        mt = materials.create(pid, new MaterialService.MaterialInput("Керамогранит 600×600", "м²", new BigDecimal("72"), new BigDecimal("9800"), "Керамика KZ", LocalDate.now().plusDays(1)), company);
        materials.order(mt.id(), company); materials.receive(mt.id(), new MaterialService.ReceiveInput(new BigDecimal("40"), null, LocalDate.now()), company);
        mt = materials.create(pid, new MaterialService.MaterialInput("Ламинат 33 класс", "м²", new BigDecimal("52"), new BigDecimal("6200"), "Паркет-Центр", LocalDate.now().plusDays(4)), company);
        materials.order(mt.id(), company);
        mt = materials.create(pid, new MaterialService.MaterialInput("Краска интерьерная 9 л", "банка", new BigDecimal("14"), new BigDecimal("18900"), "ЛакоКраска", LocalDate.now().minusDays(12)), company);
        materials.order(mt.id(), company); materials.receive(mt.id(), new MaterialService.ReceiveInput(new BigDecimal("14"), null, null), company);
        materials.create(pid, new MaterialService.MaterialInput("Смесители и сантехника", "компл", new BigDecimal("3"), new BigDecimal("145000"), "Сантехника-Люкс", LocalDate.now().plusDays(10)), company);
        materials.create(pid, new MaterialService.MaterialInput("Двери межкомнатные", "шт", new BigDecimal("5"), new BigDecimal("78000"), "Двери KZ", LocalDate.now().plusDays(18)), company);

        inspections.create(pid, new InspectionService.InspectionInput("Приёмка электрики", stageList.get(3).id(), LocalDate.now().minusDays(25), Result.PASSED, "Щиток и разводка выполнены по проекту.", null), as(inspector));
        inspections.create(pid, new InspectionService.InspectionInput("Проверка штукатурки", stageList.get(4).id(), LocalDate.now().minusDays(18), Result.NEEDS_IMPROVEMENT, "Отклонение по плоскости до 4 мм на стене кухни.", "Дошпаклевать и пройти шлифовкой, повторная проверка."), as(inspector));
        inspections.create(pid, new InspectionService.InspectionInput("Гидроизоляция санузла", stageList.get(3).id(), LocalDate.now().minusDays(22), Result.PASSED, "Заливка пола проверена, течей нет.", null), as(inspector));
        inspections.create(pid, new InspectionService.InspectionInput("Проверка отделочных работ", stageList.get(5).id(), LocalDate.now().plusDays(2), Result.PENDING, null, null), as(inspector));

        Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        calendar.create(pid, new CalendarService.EventInput("CONTRACTOR_VISIT", "Выезд прораба на объект", tomorrow.plus(3, ChronoUnit.HOURS), tomorrow.plus(5, ChronoUnit.HOURS)), company);
        calendar.create(pid, new CalendarService.EventInput("PAYMENT_DEADLINE", "Срок оплаты этапа «Полы и плитка»", tomorrow.plus(72, ChronoUnit.HOURS), null), company);

        photo(pid, stageList.get(0).id(), "BEFORE", "Состояние до ремонта", new Color(0x8A9BA8), asel.getId());
        photo(pid, stageList.get(1).id(), "DURING", "Демонтаж перегородок завершён", new Color(0xC9A27A), stroy.getId());
        photo(pid, stageList.get(3).id(), "DURING", "Разводка электрики, щиток", new Color(0x2477D4), alexey.getId());
        photo(pid, stageList.get(4).id(), "STAGE", "Штукатурка гостиной", new Color(0xE99545), stroy.getId());
        photo(pid, stageList.get(5).id(), "DURING", "Покраска спальни", new Color(0x36966B), ivan.getId());
        photo(pid, stageList.get(6).id(), "DURING", "Начало укладки плитки в санузле", new Color(0x183B50), dmitry.getId());

        var conv = messaging.projectConversation(pid, customer);
        messaging.projectConversation(pid, company);
        messaging.send(conv.id(), "Здравствуйте! Начинаем плиточные работы завтра в 10:00.", null, company);
        messaging.send(conv.id(), "Отлично, буду дома.", null, customer);
        messaging.send(conv.id(), "Вот список материалов, которые нужно докупить: затирка, клей, крестики.", null, company);
        messaging.send(conv.id(), "Спасибо! Закажу сегодня.", null, customer);

        // --------- Project 2: bathroom, proposals received ---------
        var c2 = as(daniyar);
        var p2 = projectService.create(new ProjectService.ProjectInput("Ванная комната 6 м²", "APARTMENT", 1, new BigDecimal("6"), "Алматы, ул. Тимирязева 42, кв. 12", "Almaty",
            "Полная замена сантехники, плитка стены/пол, гидроизоляция.", "BATHROOM", new BigDecimal("1200000"), new BigDecimal("2000000"), true, "HIGH", LocalDate.now().plusDays(14), null), c2);
        projectService.submit(p2.id(), c2);
        projectService.propose(p2.id(), new ProjectService.ProposalInput(new BigDecimal("1750000"), 18, "Выполним за 18 дней, материалы по смете."), as(remont));
        projectService.propose(p2.id(), new ProjectService.ProposalInput(new BigDecimal("1580000"), 22, "Укладка плитки и сантехника, гарантия 24 месяца."), as(dmitry));

        // --------- Project 3: house, newly submitted; Project 4: draft ---------
        var c3 = as(aigerim);
        var p3 = projectService.create(new ProjectService.ProjectInput("Дом 180 м², Алматинская область", "HOUSE", 6, new BigDecimal("180"), "Алматинская обл., Талгарский р-н, п. Бесагаш", "Almaty",
            "Внутренняя отделка двухэтажного дома, электрика и отопление.", "HOUSE_RENOVATION", new BigDecimal("18000000"), new BigDecimal("26000000"), true, "NORMAL", LocalDate.now().plusDays(30), null), c3);
        projectService.submit(p3.id(), c3);
        projectService.create(new ProjectService.ProjectInput("Кухня 9 м² (черновик)", "APARTMENT", 1, new BigDecimal("9"), null, "Almaty", null, "KITCHEN", null, null, true, "NORMAL", null, null), c2);

        // --------- Project 5: completed kitchen with review ---------
        var p5 = projectService.create(new ProjectService.ProjectInput("Кухня 12 м²", "APARTMENT", 1, new BigDecimal("12"), "Алматы, мкр. Орбита-1, д. 8", "Almaty", "Кухня под ключ.", "KITCHEN",
            new BigDecimal("2000000"), new BigDecimal("3000000"), true, "NORMAL", LocalDate.now().minusDays(120), null), customer);
        projectService.submit(p5.id(), customer);
        var pr5 = projectService.propose(p5.id(), new ProjectService.ProposalInput(new BigDecimal("2800000"), 30, "Кухня под ключ за месяц."), as(remont));
        projectService.accept(pr5.id(), customer);
        var e5 = estimates.create(p5.id(), new EstimateService.EstimateInput(List.of(li(Kind.LABOR, "Работы по кухне", "1", "компл", "1500000"), li(Kind.MATERIAL, "Материалы", "1", "компл", "1300000")), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), as(remont));
        estimates.submit(e5.id(), as(remont)); estimates.approve(e5.id(), customer);
        for (var s : stageService.list(p5.id(), customer)) {
            stageService.update(s.id(), new StageService.StageUpdate(null, null, null, null, null, null, 100, null, null, null, null), as(remont));
            stageService.update(s.id(), new StageService.StageUpdate(null, null, null, null, null, null, null, StageStatus.COMPLETED, null, null, null), customer);
        }
        pay(p5.id(), "Оплата работ", Type.FINAL, Method.BANK_TRANSFER, "2800000", customer, as(remont));
        projectService.complete(p5.id(), customer);
        masters.review(p5.id(), new MasterService.ReviewInput(5, "Сделали быстро и аккуратно, всё по смете. Рекомендую!"), customer);

        // demo flag on all demo-created rows
        jdbc.update("update projects set demo = true");
        log.info("Demo data ready. Log in with +77010000001 / {} (customer), +77010000009 (company), +77010000099 (admin)", PASSWORD);
    }

    private static ItemInput li(Kind k, String title, String qty, String unit, String price) {
        return new ItemInput(k, title, null, new BigDecimal(qty), unit, new BigDecimal(price));
    }

    private void pay(UUID pid, String note, Type type, Method method, String amount, AuthUser payer, AuthUser contractor) {
        var p = payments.pay(pid, new PaymentService.PayCommand(type, method, new BigDecimal(amount), null, note, "demo-" + UUID.randomUUID()), payer);
        payments.confirmManual(p.id(), contractor);
    }

    /** Placeholder "photo" rendered with Java2D, clearly watermarked as demo. */
    private void photo(UUID pid, UUID stageId, String category, String caption, Color c, UUID uploader) throws Exception {
        BufferedImage img = new BufferedImage(960, 640, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, c.brighter(), 960, 640, c.darker()));
        g.fillRect(0, 0, 960, 640);
        g.setColor(new Color(255, 255, 255, 60));
        for (int i = 0; i < 6; i++) g.fillRect(80 + i * 140, 360 - i * 30, 90, 200 + i * 30);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 40));
        g.drawString("DEMO · " + category, 40, 80);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28));
        g.drawString(caption, 40, 130);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        byte[] bytes = out.toByteArray();
        Media m = new Media();
        m.setProjectId(pid); m.setStageId(stageId); m.setUploaderId(uploader); m.setCategory(category); m.setFilename("demo-" + category.toLowerCase() + ".png");
        m.setContentType("image/png"); m.setSizeBytes(bytes.length); m.setCaption(caption); m.setStorageKey(pid + "/" + m.getId() + ".png");
        storage.put(m.getStorageKey(), new ByteArrayInputStream(bytes), bytes.length, "image/png");
        mediaRepo.save(m);
    }
}
