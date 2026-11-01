package com.narturebelle.payments.model;

import jakarta.persistence.*;

/** A plan tier (Standard/Care/Premium). Pricing/sessions live on PlanVersion, never here, so prices can change without renaming the tier. */
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private PlanCode code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer displayOrder;

    public Plan() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PlanCode getCode() { return code; }
    public void setCode(PlanCode code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
}
