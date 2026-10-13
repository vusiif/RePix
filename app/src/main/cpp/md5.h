#ifndef REPIX_MD5_H
#define REPIX_MD5_H

#include <cstdint>
#include <cstddef>
#include <string>
#include <array>

namespace repix {

class MD5 {
public:
    MD5();
    void update(const uint8_t* data, size_t length);
    void update(const std::string& str);
    void finalize();
    
    // Returns the 16 raw digest bytes
    const std::array<uint8_t, 16>& digest_bytes() const { return digest_; }

    // Computes digest directly
    static std::array<uint8_t, 16> compute(const uint8_t* data, size_t length);
    static std::array<uint8_t, 16> compute(const std::string& str);

    // Specifically for RePix Fisher-Yates shuffle:
    // Derives: Integer.parseInt(hex.substring(0, 7), 16) from the MD5 digest
    static uint32_t parse_first_7_hex(const std::array<uint8_t, 16>& digest);

private:
    void transform(const uint8_t block[64]);

    uint32_t state_[4];
    uint64_t count_;
    uint8_t buffer_[64];
    std::array<uint8_t, 16> digest_;
    bool finalized_;
};

} // namespace repix

#endif // REPIX_MD5_H
