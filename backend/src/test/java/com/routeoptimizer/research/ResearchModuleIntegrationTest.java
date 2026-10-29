package com.routeoptimizer.research;

import com.routeoptimizer.entity.Driver;
import com.routeoptimizer.entity.Location;
import com.routeoptimizer.entity.User;
import com.routeoptimizer.enums.Role;
import com.routeoptimizer.repository.DriverRepository;
import com.routeoptimizer.repository.LocationRepository;
import com.routeoptimizer.repository.UserRepository;
import com.routeoptimizer.research.domain.HistoricalRoute;
import com.routeoptimizer.research.domain.HistoricalTask;
import com.routeoptimizer.research.domain.ResearchTaskType;
import com.routeoptimizer.research.repository.HistoricalRouteRepository;
import com.routeoptimizer.research.repository.HistoricalTaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public class ResearchModuleIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HistoricalRouteRepository historicalRouteRepository;

    @Autowired
    private HistoricalTaskRepository historicalTaskRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LocationRepository locationRepository;

    private static final String[] REQUIRED_TABLES = {
        "research_historical_tasks",
        "research_historical_routes",
        "drivers",
        "users",
        "locations"
    };

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        for (String table : REQUIRED_TABLES) {
            com.routeoptimizer.TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
        jdbcTemplate.execute("TRUNCATE TABLE " + table + " CASCADE");
        }
    }

    @Test
    void testMigrationSucceedsAndTablesExist() {
        Integer routesCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM research_historical_routes", Integer.class);
        Integer tasksCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM research_historical_tasks", Integer.class);
        
        assertThat(routesCount).isNotNull().isEqualTo(0);
        assertThat(tasksCount).isNotNull().isEqualTo(0);
    }

    @Test
    void testResearchRecordsPersistenceAndRelationships() {
        User user = User.builder()
                .username("testdriver")
                .email("testdriver@example.com")
                .password("password")
                .role(Role.DRIVER)
                .build();
        user = userRepository.save(user);

        Driver driver = Driver.builder()
                .user(user)
                .name("Test Driver")
                .status(com.routeoptimizer.enums.DriverStatus.AVAILABLE)
                .build();
        driver = driverRepository.save(driver);

        Location loc1 = Location.builder()
                .name("Loc A")
                .latitude(10.0)
                .longitude(20.0)
                .build();
        loc1 = locationRepository.save(loc1);

        HistoricalRoute route = HistoricalRoute.builder()
                .driver(driver)
                .totalDistance(15.5)
                .totalTime(45.0)
                .routeDate(OffsetDateTime.now())
                .build();
        
        HistoricalTask task1 = HistoricalTask.builder()
                .historicalRoute(route)
                .location(loc1)
                .taskType(ResearchTaskType.PICKUP)
                .sequenceOrder(1)
                .build();

        route.getTasks().add(task1);

        HistoricalRoute savedRoute = historicalRouteRepository.save(route);

        assertThat(savedRoute.getId()).isNotNull();
        assertThat(savedRoute.getTasks()).hasSize(1);
        assertThat(savedRoute.getTasks().get(0).getId()).isNotNull();

        List<HistoricalRoute> driverRoutes = historicalRouteRepository.findByDriverId(driver.getId());
        assertThat(driverRoutes).hasSize(1);
        assertThat(driverRoutes.get(0).getTotalDistance()).isEqualTo(15.5);
    }
}
