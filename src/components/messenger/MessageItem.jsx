import React from 'react';
import './MessageItem.css';

function MessageItem({ message, isOwn, showAvatar }) {
    const formatTime = (timestamp) => {
        if (!timestamp) return '';
        const date = new Date(timestamp);
        return date.toLocaleTimeString('ko-KR', {
            hour: '2-digit',
            minute: '2-digit'
        });
    };

    return (
        <div className={`message-item ${isOwn ? 'own' : 'other'}`}>
            {!isOwn && (
                <div className="message-avatar-container">
                    {showAvatar ? (
                        <div className="message-avatar">
                            {message.senderName?.charAt(0) || '?'}
                        </div>
                    ) : (
                        <div className="avatar-placeholder" />
                    )}
                </div>
            )}

            <div className="message-content-wrapper">
                {!isOwn && showAvatar && (
                    <div className="message-sender-name">
                        {message.senderName || '알 수 없음'}
                    </div>
                )}

                <div className="message-bubble-row">
                    <div className="message-bubble">
                        <p className="message-text">{message.content}</p>
                    </div>

                    <span className="message-time">
                        {formatTime(message.createdAt)}
                    </span>
                </div>
            </div>
        </div>
    );
}

export default MessageItem;
