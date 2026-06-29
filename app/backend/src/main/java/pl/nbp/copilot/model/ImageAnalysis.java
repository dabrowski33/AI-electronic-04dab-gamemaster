package pl.nbp.copilot.model;

import java.util.List;

public record ImageAnalysis(
    String summary,
    List<String> observations,
    Confidence confidence,
    TriState signsOfUse,
    TriState visibleDamage,
    TriState complete,
    TriState resellableAsNew,
    String damageType,
    LikelyCause likelyCause
) {}
