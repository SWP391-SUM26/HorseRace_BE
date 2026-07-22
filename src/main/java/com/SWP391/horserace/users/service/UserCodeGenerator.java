package com.SWP391.horserace.users.service;

import com.SWP391.horserace.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The single source of {@code app_user.user_code}.
 *
 * <p>Three generators used to coexist: {@code StaffServiceImpl} produced the sequential
 * {@code USR0009} while {@code AuthServiceImpl} and {@code UserServiceImpl} produced a random
 * {@code USR-A3F91C2D}. The same column therefore held two incompatible shapes depending on which
 * path created the user. The sequential form wins — it is what the seed data and every existing row
 * uses, and it reads as an ID rather than as noise.
 *
 * <p>The old sequential version was also the only code generator in the codebase without a
 * uniqueness loop: it returned {@code count() + 1} in one shot. {@code count()} includes
 * soft-deleted rows while the codes are dense, so any gap silently collided with the
 * {@code UNIQUE} constraint. This one skips taken codes, matching every other generate*Code method.
 */
@Component
@RequiredArgsConstructor
public class UserCodeGenerator {

    private final UserRepository userRepository;

    /** Next free sequential code in the form {@code USR0001}. */
    public String generate() {
        long n = userRepository.count() + 1;
        String code;
        do {
            code = String.format("USR%04d", n++);
        } while (userRepository.existsByUserCode(code));
        return code;
    }
}
