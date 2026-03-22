(() => {
    const messagesEl = document.getElementById("messages");
    const myIpEl = document.getElementById("my-ip");
    const statusEl = document.getElementById("status");
    const textEl = document.getElementById("text");
    const sendBtn = document.getElementById("send");

    let stompClient = null;
    let isComposing = false;
    let myIp = "unknown";

    function formatTime(iso) {
        if (!iso) {
            return "";
        }
        return new Date(iso).toLocaleString();
    }

    function resolveMessageRef(message) {
        return message.messageRef || message.messageKey || (message.id == null ? null : String(message.id));
    }

    function messageElementId(messageRef) {
        return `message-${messageRef}`;
    }

    function removeMessage(messageRef) {
        if (messageRef == null) {
            return;
        }
        const existing = document.getElementById(messageElementId(messageRef));
        if (existing) {
            existing.remove();
        }
    }

    function canDeleteMessage(message) {
        return Boolean(message && resolveMessageRef(message) && message.senderIp === myIp);
    }

    async function deleteMessage(messageRef) {
        try {
            const res = await fetch(`/api/messages/${encodeURIComponent(messageRef)}`, {method: "DELETE"});
            if (!res.ok) {
                if (res.status === 403) {
                    throw new Error("자기 메시지만 삭제할 수 있습니다.");
                }
                if (res.status === 404) {
                    throw new Error("이미 삭제된 메시지입니다.");
                }
                throw new Error("메시지 삭제 실패");
            }

            removeMessage(messageRef);
        } catch (e) {
            statusEl.textContent = e.message || "메시지 삭제 실패";
        }
    }

    function buildMessageElement(message) {
        const li = document.createElement("li");
        const messageRef = resolveMessageRef(message);
        if (messageRef) {
            li.id = messageElementId(messageRef);
        }

        const textSpan = document.createElement("span");
        textSpan.textContent = `[${formatTime(message.sentAt)}] (${message.senderIp}) ${message.content}`;
        li.appendChild(textSpan);

        if (canDeleteMessage(message)) {
            const deleteBtn = document.createElement("button");
            deleteBtn.type = "button";
            deleteBtn.className = "delete-btn";
            deleteBtn.textContent = "삭제";
            deleteBtn.addEventListener("click", () => {
                deleteMessage(messageRef);
            });
            li.appendChild(deleteBtn);
        }

        return li;
    }

    function appendMessage(message, options = {}) {
        const {scroll = true} = options;
        const li = buildMessageElement(message);
        const messageRef = resolveMessageRef(message);
        const existing = messageRef == null ? null : document.getElementById(messageElementId(messageRef));

        if (existing) {
            existing.replaceWith(li);
        } else {
            messagesEl.appendChild(li);
        }

        if (scroll) {
            li.scrollIntoView({block: "end"});
        }
    }

    async function loadMyIp() {
        try {
            const res = await fetch("/api/me");
            if (!res.ok) {
                throw new Error("Failed to load ip");
            }
            const data = await res.json();
            myIp = data.ip || "unknown";
            myIpEl.textContent = myIp;
        } catch (e) {
            myIp = "unknown";
            myIpEl.textContent = "unknown";
        }
    }

    async function loadHistory() {
        try {
            const res = await fetch("/api/messages?limit=200");
            if (!res.ok) {
                throw new Error("Failed to load history");
            }
            const history = await res.json();
            history.forEach((message) => appendMessage(message, {scroll: false}));
            if (messagesEl.lastElementChild) {
                messagesEl.lastElementChild.scrollIntoView({block: "end"});
            }
        } catch (e) {
            statusEl.textContent = "히스토리 로딩 실패";
        }
    }

    function connect() {
        stompClient = new StompJs.Client({
            webSocketFactory: () => new SockJS("/ws-chat"),
            reconnectDelay: 3000,
            onConnect: () => {
                statusEl.textContent = "연결됨";
                stompClient.subscribe("/topic/public", (frame) => {
                    const payload = JSON.parse(frame.body);
                    appendMessage(payload);
                });
                stompClient.subscribe("/topic/public.delete", (frame) => {
                    const payload = JSON.parse(frame.body);
                    removeMessage(payload.messageRef);
                });
            },
            onStompError: (frame) => {
                statusEl.textContent = `오류: ${frame.headers.message || "unknown"}`;
            },
            onWebSocketClose: () => {
                statusEl.textContent = "연결 종료, 재연결 시도 중...";
            }
        });

        stompClient.activate();
    }

    function sendMessage() {
        const content = textEl.value.trim();
        if (!content || !stompClient || !stompClient.connected) {
            return;
        }

        stompClient.publish({
            destination: "/app/chat.send",
            body: JSON.stringify({content})
        });

        textEl.value = "";
        textEl.focus();
    }

    sendBtn.addEventListener("click", sendMessage);
    textEl.addEventListener("compositionstart", () => {
        isComposing = true;
    });
    textEl.addEventListener("compositionend", () => {
        isComposing = false;
    });
    textEl.addEventListener("keydown", (event) => {
        if (event.key !== "Enter") {
            return;
        }
        if (event.isComposing || isComposing || event.keyCode === 229) {
            return;
        }
        event.preventDefault();
        if (!event.repeat) {
            sendMessage();
        }
    });

    async function init() {
        await loadMyIp();
        connect();
        await loadHistory();
    }

    init();
})();
