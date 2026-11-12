import Foundation
import Security
import WebKit

private struct StoredCookie: Codable {
    let name: String
    let value: String
    let domain: String
    let path: String
    let expires: Date?
    let secure: Bool

    init(_ cookie: HTTPCookie) {
        name = cookie.name
        value = cookie.value
        domain = cookie.domain
        path = cookie.path
        expires = cookie.expiresDate
        secure = cookie.isSecure
    }

    var cookie: HTTPCookie? {
        var properties: [HTTPCookiePropertyKey: Any] = [
            .name: name, .value: value, .domain: domain, .path: path,
            .secure: secure ? "TRUE" : "FALSE"
        ]
        if let expires { properties[.expires] = expires }
        return HTTPCookie(properties: properties)
    }
}

enum WebClassCookieVault {
    private static let service = "com.webclass.plus.ios.webclass-session"
    private static let account = "cookies"

    static func restore() async {
        guard let data = loadData(), let records = try? JSONDecoder().decode([StoredCookie].self, from: data) else { return }
        let store = WKWebsiteDataStore.default().httpCookieStore
        for record in records {
            if let cookie = record.cookie { await set(cookie, in: store) }
        }
    }

    static func save() async {
        let cookies = await allCookies(in: WKWebsiteDataStore.default().httpCookieStore)
            .filter { $0.domain.contains("webclass.kosen-k.go.jp") }
            .map(StoredCookie.init)
        guard let data = try? JSONEncoder().encode(cookies) else { return }
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account]
        SecItemDelete(query as CFDictionary)
        var item = query
        item[kSecValueData as String] = data
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(item as CFDictionary, nil)
    }

    static func clear() async {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account]
        SecItemDelete(query as CFDictionary)
        let store = WKWebsiteDataStore.default().httpCookieStore
        let cookies = await allCookies(in: store).filter { $0.domain.contains("webclass.kosen-k.go.jp") }
        for cookie in cookies { await delete(cookie, in: store) }
    }

    private static func loadData() -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess else { return nil }
        return result as? Data
    }

    private static func allCookies(in store: WKHTTPCookieStore) async -> [HTTPCookie] {
        await withCheckedContinuation { continuation in store.getAllCookies { continuation.resume(returning: $0) } }
    }

    private static func set(_ cookie: HTTPCookie, in store: WKHTTPCookieStore) async {
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in store.setCookie(cookie) { continuation.resume() } }
    }

    private static func delete(_ cookie: HTTPCookie, in store: WKHTTPCookieStore) async {
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in store.delete(cookie) { continuation.resume() } }
    }
}
