package com.chacha.jadeime.emoji

/** Common Unicode emoji set; source data can later be generated from emoji-test.txt. */
internal object EmojiCatalog {
    private fun list(value: String) = value.split(" ")
    val categories = listOf(
        EmojiCategory("smileys", "表情", list("😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 ☹️ 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕")),
        EmojiCategory("animals", "动物", list("🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐒 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🕷️ 🦂 🐢 🐍 🦎 🦖 🦕 🐙 🦑 🦀 🐠 🐟 🐡 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦏 🦛 🐪 🐫 🦒 🦘 🦬 🐃 🐂 🐄 🐎 🐖 🐏 🐑 🦙 🐐 🦌 🐕 🐩 🐈 🐓 🦃 🕊️ 🐇 🐁 🐀 🐿️ 🦔")),
        EmojiCategory("food", "食物", list("🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥕 🌽 🌶️ 🍔 🍟 🍕 🌭 🌮 🍿 🍣 🍚 🍜 🍦 🍩 🍪 🎂 🍰 ☕ 🍺 🍻 🍷 🥤")),
        EmojiCategory("plants", "植物", list("🌱 🌿 ☘️ 🍀 🎋 🎍 🌾 🌵 🌴 🌳 🌲 🌷 🌹 🌺 🌸 🌼 🌻 🪻 🪷 🥀 💐 🍁 🍂 🍃 🪴")),
        EmojiCategory("symbols", "符号", list("❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ✨ ⭐ 🌟 💫 🔥 🎉 ✅ ❌ ⚠️ ❗ ❓ ‼️ ⁉️ 💯 ✔️ ☑️ ➕ ➖ ✖️ ➗ ♻️ ©️ ®️ ™️")),
        EmojiCategory("flags", "旗帜", list("🇨🇳 🇺🇸 🇯🇵 🇬🇧 🇰🇷 🇫🇷 🇩🇪 🇮🇹 🇨🇦 🇦🇺")),
    )
    const val MAX_RECENT = 40
}
