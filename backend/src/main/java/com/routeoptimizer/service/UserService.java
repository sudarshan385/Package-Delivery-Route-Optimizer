package com.routeoptimizer.service;

import com.routeoptimizer.dto.CreateUserRequest;
import com.routeoptimizer.dto.UserResponse;
import com.routeoptimizer.entity.Driver;
import com.routeoptimizer.entity.User;
import com.routeoptimizer.enums.DriverStatus;
import com.routeoptimizer.enums.Role;
import com.routeoptimizer.exception.BadRequestException;
import com.routeoptimizer.exception.ConflictException;
import com.routeoptimizer.exception.ResourceNotFoundException;
import com.routeoptimizer.repository.DriverRepository;
import com.routeoptimizer.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final PasswordEncoder passwordEncoder;

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id", "username", "email", "fullName", "role", "active", "isVerified", "isBlocked", "createdAt", "updatedAt"
    );

    /**
     * Creates a user. Section 44: Only ADMIN may create users.
     * Section 45: A DRIVER user account must be associated with a Driver profile transactionally.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username '" + request.getUsername() + "' is already in use.");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email '" + request.getEmail() + "' is already registered.");
        }

        // Section 45: DRIVER role must have an associated Driver profile
        if (request.getRole() == Role.DRIVER) {
            if (!StringUtils.hasText(request.getLicenseNumber())) {
                throw new BadRequestException("License number is required to create a Driver profile for a DRIVER user.");
            }
            if (driverRepository.findByLicenseNumber(request.getLicenseNumber().trim()).isPresent()) {
                throw new ConflictException("Driver license number '" + request.getLicenseNumber() + "' is already in use.");
            }
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(request.getRole())
                .isVerified(request.getRole() == Role.ADMIN)
                .isBlocked(false)
                .build();
        user.setActive(true);
        user.setVersion(0L);

        User savedUser = userRepository.save(user);
        Long driverId = null;

        if (request.getRole() == Role.DRIVER) {
            Driver driver = Driver.builder()
                    .user(savedUser)
                    .name(StringUtils.hasText(request.getFullName()) ? request.getFullName() : request.getUsername())
                    .phone(request.getPhone())
                    .licenseNumber(request.getLicenseNumber().trim())
                    .status(DriverStatus.AVAILABLE)
                    .build();
            driver.setActive(true);
            driver.setVersion(0L);

            Driver savedDriver = driverRepository.save(driver);
            driverId = savedDriver.getId();
        }

        return toUserResponse(savedUser, driverId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(
            String search,
            Role role,
            Boolean active,
            Boolean isVerified,
            Boolean isBlocked,
            Pageable pageable) {

        validateSort(pageable);

        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(search)) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate usernamePredicate = cb.like(cb.lower(root.get("username")), pattern);
                Predicate emailPredicate = cb.like(cb.lower(root.get("email")), pattern);
                Predicate fullNamePredicate = cb.like(cb.lower(root.get("fullName")), pattern);
                predicates.add(cb.or(usernamePredicate, emailPredicate, fullNamePredicate));
            }

            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }

            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            if (isVerified != null) {
                predicates.add(cb.equal(root.get("isVerified"), isVerified));
            }

            if (isBlocked != null) {
                predicates.add(cb.equal(root.get("isBlocked"), isBlocked));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<User> page = userRepository.findAll(spec, pageable);
        return page.map(u -> {
            Long driverId = driverRepository.findByUserId(u.getId()).map(Driver::getId).orElse(null);
            return toUserResponse(u, driverId);
        });
    }

    @PreAuthorize("hasRole('ADMIN') or (hasRole('DRIVER') and #id == authentication.principal.id)")
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        Long driverId = driverRepository.findByUserId(user.getId()).map(Driver::getId).orElse(null);
        return toUserResponse(user, driverId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse verifyUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.setIsVerified(true);
        User savedUser = userRepository.save(user);
        Long driverId = driverRepository.findByUserId(savedUser.getId()).map(Driver::getId).orElse(null);
        return toUserResponse(savedUser, driverId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse unverifyUser(Long id, String adminUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        User currentAdmin = userRepository.findByUsernameIgnoreCase(adminUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Current administrator not found: " + adminUsername));

        if (user.getId().equals(currentAdmin.getId())) {
            throw new BadRequestException("Administrators cannot unverify their own account.");
        }

        ensureNotLastUsableAdmin(user);

        user.setIsVerified(false);
        User savedUser = userRepository.save(user);
        Long driverId = driverRepository.findByUserId(savedUser.getId()).map(Driver::getId).orElse(null);
        return toUserResponse(savedUser, driverId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse blockUser(Long id, String adminUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        User currentAdmin = userRepository.findByUsernameIgnoreCase(adminUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Current administrator not found: " + adminUsername));

        if (user.getId().equals(currentAdmin.getId())) {
            throw new BadRequestException("Administrators cannot block their own account.");
        }

        ensureNotLastUsableAdmin(user);

        user.setIsBlocked(true);
        User savedUser = userRepository.save(user);
        Long driverId = driverRepository.findByUserId(savedUser.getId()).map(Driver::getId).orElse(null);
        return toUserResponse(savedUser, driverId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse unblockUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.setIsBlocked(false);
        User savedUser = userRepository.save(user);
        Long driverId = driverRepository.findByUserId(savedUser.getId()).map(Driver::getId).orElse(null);
        return toUserResponse(savedUser, driverId);
    }

    @Transactional(readOnly = true)
    public UserResponse getMyProfile(String username) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        Long driverId = driverRepository.findByUserId(user.getId()).map(Driver::getId).orElse(null);
        return toUserResponse(user, driverId);
    }

    @Transactional
    public void changePassword(String username, com.routeoptimizer.dto.ChangePasswordRequest request) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new org.springframework.security.authentication.BadCredentialsException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    /**
     * Prevents blocking or unverifying the last usable administrator account.
     * Uses pessimistic write lock on all usable administrators for strict concurrency safety.
     */
    private void ensureNotLastUsableAdmin(User targetUser) {
        if (targetUser.getRole() == Role.ADMIN) {
            boolean isCurrentlyUsable = Boolean.TRUE.equals(targetUser.getActive())
                    && !Boolean.TRUE.equals(targetUser.getIsBlocked())
                    && Boolean.TRUE.equals(targetUser.getIsVerified());

            if (isCurrentlyUsable) {
                List<User> usableAdmins = userRepository.findUsableAdminsForUpdate(Role.ADMIN);
                boolean targetInUsableList = usableAdmins.stream().anyMatch(u -> u.getId().equals(targetUser.getId()));
                if (targetInUsableList && usableAdmins.size() <= 1) {
                    throw new BadRequestException("Cannot disable, block, or unverify the last usable administrator account.");
                }
            }
        }
    }

    private void validateSort(Pageable pageable) {
        if (pageable != null && pageable.getSort() != null) {
            for (Sort.Order order : pageable.getSort()) {
                if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                    throw new BadRequestException("Invalid sort field: '" + order.getProperty() + "'");
                }
            }
        }
    }

    private UserResponse toUserResponse(User user, Long driverId) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .active(user.getActive())
                .isVerified(Boolean.TRUE.equals(user.getIsVerified()))
                .isBlocked(Boolean.TRUE.equals(user.getIsBlocked()))
                .driverId(driverId)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
