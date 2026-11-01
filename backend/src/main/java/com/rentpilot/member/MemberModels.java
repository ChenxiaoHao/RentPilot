package com.rentpilot.member;

import com.rentpilot.listing.Listing;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class MemberModels {
    private MemberModels() {}
    public record Credentials(@NotBlank @Pattern(regexp="[A-Za-z][A-Za-z0-9_]{2,23}") String username,
                              @NotBlank @Size(min=8,max=72) String password,
                              @Size(max=16) String nickname) {}
    public record Nickname(@NotBlank @Size(max=16) String nickname) {}
    public record Profile(String id,String username,String nickname,OffsetDateTime createdAt,
                          int favoritesCount,int historyCount,int plannedCount) {}
    public record Session(String token,OffsetDateTime expiresAt,Profile profile) {}
    public record SavedListing(Listing listing,OffsetDateTime recordedAt) {}
    public record Page<T>(List<T> items,int page,int size,int total,int totalPages) {}
    public record ViewingInput(@NotBlank String listingId,@NotNull LocalDate date,
                               @NotBlank @Pattern(regexp="MORNING|AFTERNOON|EVENING") String slot) {}
    public record StatusInput(@NotBlank @Pattern(regexp="CANCELLED|COMPLETED") String status) {}
    public record Viewing(String id,Listing listing,LocalDate date,String slot,String status,OffsetDateTime updatedAt) {}
}
