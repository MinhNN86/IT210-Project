-- ==========================================================================
-- DATABASE CREATION
-- ==========================================================================

CREATE DATABASE IF NOT EXISTS smart_academic_platform;
USE smart_academic_platform;


-- ==========================================================================
-- USERS TABLE (Combined with user_profiles and roles)
-- ID dùng UUID (CHAR(36)) thay vì BIGINT AUTO_INCREMENT
-- ==========================================================================

CREATE TABLE users (
    id CHAR(36) PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('ADMIN', 'LECTURER', 'STUDENT') NOT NULL DEFAULT 'STUDENT',
    full_name VARCHAR(100),
    phone VARCHAR(20),
    gender ENUM('MALE', 'FEMALE', 'OTHER'),
    dob DATE,
    avatar_url VARCHAR(255),
    address TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    two_factor_enabled BOOLEAN DEFAULT FALSE,
    two_factor_secret VARCHAR(100) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ==========================================================================
-- DEPARTMENTS TABLE
-- ==========================================================================

CREATE TABLE departments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(20) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL
);

-- ==========================================================================
-- LECTURERS TABLE
-- ==========================================================================

CREATE TABLE lecturers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id CHAR(36) UNIQUE NOT NULL,
    department_id BIGINT NOT NULL,
    academic_rank VARCHAR(100),
    specialization TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lecturer_user
       FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_lecturer_department
       FOREIGN KEY (department_id) REFERENCES departments(id)
);


-- ==========================================================================
-- EQUIPMENTS TABLE
-- ==========================================================================

CREATE TABLE equipments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    quantity_in_stock INT NOT NULL DEFAULT 0,
    minimum_stock INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);


-- ==========================================================================
-- MENTORING SESSIONS TABLE
-- ==========================================================================

CREATE TABLE mentoring_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id CHAR(36) NOT NULL,
    lecturer_id BIGINT NOT NULL,
    session_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    status ENUM('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED')
        NOT NULL DEFAULT 'PENDING',
    note TEXT,
    cancel_reason TEXT,
    cancelled_at TIMESTAMP NULL,
    meeting_active BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_student
        FOREIGN KEY (student_id) REFERENCES users(id),
    CONSTRAINT fk_session_lecturer
        FOREIGN KEY (lecturer_id) REFERENCES lecturers(id)
);


-- ==========================================================================
-- ACADEMIC EVALUATIONS TABLE
-- ==========================================================================

CREATE TABLE academic_evaluations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mentoring_session_id BIGINT UNIQUE NOT NULL,
    lecturer_id BIGINT NOT NULL,
    student_id CHAR(36) NOT NULL,
    performance_level VARCHAR(50),
    evaluation_comment TEXT,
    recommendation TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_eval_session
      FOREIGN KEY (mentoring_session_id) REFERENCES mentoring_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_eval_lecturer
      FOREIGN KEY (lecturer_id) REFERENCES lecturers(id),
    CONSTRAINT fk_eval_student
      FOREIGN KEY (student_id) REFERENCES users(id)
);


-- ==========================================================================
-- BORROWING RECORDS TABLE
-- ==========================================================================

CREATE TABLE borrowing_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mentoring_session_id BIGINT UNIQUE NOT NULL,
    student_id CHAR(36) NOT NULL,
    approved_by CHAR(36) NULL,
    status ENUM('PENDING_DISPATCH', 'DISPATCHED', 'RETURNED', 'REJECTED')
                                      NOT NULL DEFAULT 'PENDING_DISPATCH',
    borrow_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expected_return_date DATE,
    actual_return_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_borrow_session
       FOREIGN KEY (mentoring_session_id) REFERENCES mentoring_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_borrow_student
       FOREIGN KEY (student_id) REFERENCES users(id),
    CONSTRAINT fk_borrow_admin
       FOREIGN KEY (approved_by) REFERENCES users(id)
);


-- ==========================================================================
-- BORROWING DETAILS TABLE
-- ==========================================================================

CREATE TABLE borrowing_details (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    borrowing_record_id BIGINT NOT NULL,
    equipment_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    CONSTRAINT fk_borrow_detail_record
       FOREIGN KEY (borrowing_record_id) REFERENCES borrowing_records(id) ON DELETE CASCADE,
    CONSTRAINT fk_borrow_detail_equipment
       FOREIGN KEY (equipment_id) REFERENCES equipments(id)
);

-- ==========================================================================
-- INDEXES FOR PERFORMANCE OPTIMIZATION
-- ==========================================================================

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_full_name ON users(full_name);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_session_student ON mentoring_sessions(student_id);
CREATE INDEX idx_session_lecturer ON mentoring_sessions(lecturer_id);
CREATE INDEX idx_session_date ON mentoring_sessions(session_date);
CREATE INDEX idx_borrow_status ON borrowing_records(status);
CREATE INDEX idx_equipment_name ON equipments(name);
