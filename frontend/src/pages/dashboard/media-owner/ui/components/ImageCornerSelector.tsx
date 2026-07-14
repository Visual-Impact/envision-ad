import React, { useState, useRef, useEffect, MouseEvent as ReactMouseEvent } from 'react';

import { Image } from '@mantine/core';

interface Point {
    x: number;
    y: number;
}

interface ImageCornerSelectorProps {
    imageUrl: string;
    initialCorners?: { tl: Point; tr: Point; br: Point; bl: Point };
    onChange: (corners: { tl: Point; tr: Point; br: Point; bl: Point }) => void;
}

const HANDLE_RADIUS = 15;

export const ImageCornerSelector: React.FC<ImageCornerSelectorProps> = ({ imageUrl, initialCorners, onChange }) => {
    const [corners, setCorners] = useState<{ tl: Point; tr: Point; br: Point; bl: Point }>(
        initialCorners || {
            tl: { x: 0.1, y: 0.1 },
            tr: { x: 0.9, y: 0.1 },
            br: { x: 0.9, y: 0.9 },
            bl: { x: 0.1, y: 0.9 },
        }
    );

    const svgRef = useRef<SVGSVGElement>(null);
    const [dragging, setDragging] = useState<keyof typeof corners | null>(null);


    // Stabilize onChange to prevent effects from firing when parent re-renders
    const onChangeRef = useRef(onChange);
    useEffect(() => {
        onChangeRef.current = onChange;
    }, [onChange]);

    useEffect(() => {
        // If we are currently dragging, do NOT update state from props.
        // This prevents the "drag -> parent update -> prop update -> state update" cycle
        // that causes the infinite loop and crash.
        if (dragging || !initialCorners) return;

        (() => {
            setCorners((prev) => {
                // Check for value equality to prevent infinite loops (since JSON.parse creates new refs)
                const isDifferent =
                    initialCorners.tl.x !== prev.tl.x || initialCorners.tl.y !== prev.tl.y ||
                    initialCorners.tr.x !== prev.tr.x || initialCorners.tr.y !== prev.tr.y ||
                    initialCorners.br.x !== prev.br.x || initialCorners.br.y !== prev.br.y ||
                    initialCorners.bl.x !== prev.bl.x || initialCorners.bl.y !== prev.bl.y;

                return isDifferent ? initialCorners : prev;
            });
        })();
    }, [initialCorners, dragging]);

    useEffect(() => {
        // Only emit changes to parent if we are actively dragging.
        // This prevents updates from props (which update 'corners' state) from echoing back to the parent
        // and causing infinite loops.
        if (dragging) {
            onChangeRef.current(corners);
        }
    }, [corners, dragging]);

    // Emit initial value on mount if needed (optional, depends on if parent needs to know defaults immediately)
    useEffect(() => {
        if (!initialCorners && corners) {
            onChangeRef.current(corners);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const getMousePosition = (evt: ReactMouseEvent | globalThis.MouseEvent) => {
        if (!svgRef.current) return { x: 0, y: 0 };
        const CTM = svgRef.current.getScreenCTM();
        if (!CTM) return { x: 0, y: 0 };
        // Transform screen coordinates to SVG local coordinates
        // This handles cases where the SVG might be scaled or transformed via CSS
        return {
            x: (evt.clientX - CTM.e) / CTM.a,
            y: (evt.clientY - CTM.f) / CTM.d,
        };
    };

    const handleMouseDown = (key: keyof typeof corners) => (e: ReactMouseEvent) => {
        e.preventDefault();
        setDragging(key);
    };

    const handleMouseMove = React.useCallback((e: globalThis.MouseEvent) => {
        if (!dragging || !svgRef.current) return;
        e.preventDefault();

        const { x, y } = getMousePosition(e);

        // Get SVG dimensions to convert pixel to percentage
        const { width, height } = svgRef.current.getBoundingClientRect();

        // Convert screen pixels to normalized 0.0 - 1.0 logic
        // We constrain it between 0 and 1 so points stay inside the image.
        const nextX = Math.max(0, Math.min(1, x / width));
        const nextY = Math.max(0, Math.min(1, y / height));

        setCorners((prev) => ({
            ...prev,
            [dragging]: { x: nextX, y: nextY },
        }));
    }, [dragging]);

    const handleMouseUp = React.useCallback(() => {
        setDragging(null);
    }, []);

    const handleTouchStart = (key: keyof typeof corners) => () => {
        setDragging(key);
    };

    const handleTouchMove = React.useCallback((e: TouchEvent) => {
        if (!dragging || !svgRef.current) return;
        if (e.cancelable) e.preventDefault();

        const touch = e.touches[0];

        const CTM = svgRef.current.getScreenCTM();
        if (!CTM) return;

        const x = (touch.clientX - CTM.e) / CTM.a;
        const y = (touch.clientY - CTM.f) / CTM.d;

        const { width, height } = svgRef.current.getBoundingClientRect();

        const nextX = Math.max(0, Math.min(1, x / width));
        const nextY = Math.max(0, Math.min(1, y / height));

        setCorners((prev) => ({
            ...prev,
            [dragging]: { x: nextX, y: nextY },
        }));
    }, [dragging]);

    const handleTouchEnd = React.useCallback(() => {
        setDragging(null);
    }, []);

    useEffect(() => {
        if (dragging) {
            window.addEventListener('mousemove', handleMouseMove);
            window.addEventListener('mouseup', handleMouseUp);
            window.addEventListener('touchmove', handleTouchMove, { passive: false });
            window.addEventListener('touchend', handleTouchEnd);
        } else {
            window.removeEventListener('mousemove', handleMouseMove);
            window.removeEventListener('mouseup', handleMouseUp);
            window.removeEventListener('touchmove', handleTouchMove);
            window.removeEventListener('touchend', handleTouchEnd);
        }
        return () => {
            window.removeEventListener('mousemove', handleMouseMove);
            window.removeEventListener('mouseup', handleMouseUp);
            window.removeEventListener('touchmove', handleTouchMove);
            window.removeEventListener('touchend', handleTouchEnd);
        };
    }, [dragging, handleMouseMove, handleMouseUp, handleTouchMove, handleTouchEnd]);

    // Convert percentage to percentage string for SVG
    const toPct = (val: number) => `${val * 100}%`;

    // Order of corners to draw lines: TL -> TR -> BR -> BL -> TL
    const cornerKeys: (keyof typeof corners)[] = ['tl', 'tr', 'br', 'bl'];

    const imageRef = useRef<HTMLImageElement>(null);
    const [svgBounds, setSvgBounds] = useState({ width: 0, height: 0, top: 0, left: 0 });

    // Update SVG bounds whenever the window resizes or image loads
    const updateBounds = () => {
        if (imageRef.current) {
            const rect = imageRef.current.getBoundingClientRect();
            const containerRect = imageRef.current.parentElement?.getBoundingClientRect();

            if (containerRect) {
                setSvgBounds({
                    width: rect.width,
                    height: rect.height,
                    top: rect.top - containerRect.top,
                    left: rect.left - containerRect.left,
                });
            }
        }
    };

    useEffect(() => {
        window.addEventListener('resize', updateBounds);
        return () => window.removeEventListener('resize', updateBounds);
    }, []);
    useEffect(() => {
        // Small timeout ensures the DOM has painted and Mantine's wrapper is ready
        const timer = setTimeout(updateBounds, 100);
        return () => clearTimeout(timer);
    }, [imageUrl]); // Re-run if the image URL changes
    return (
        <div style={{ position: 'relative', width: '100%', display: 'flex', justifyContent: 'center' }}>
            <Image
                ref={imageRef}
                src={imageUrl}
                alt="Preview"
                onLoad={updateBounds}
                style={{
                    width: 'auto',
                    height: 'auto',
                    maxWidth: '100%',
                    maxHeight: '400px',
                    display: 'block',
                }}
            />

            <svg
                ref={svgRef}
                style={{
                    position: 'absolute',
                    top: svgBounds.top,
                    left: svgBounds.left,
                    width: svgBounds.width,
                    height: svgBounds.height,
                    zIndex: 10
                }}
            >
                <defs>
                    <marker
                        id="arrow"
                        viewBox="0 0 10 10"
                        refX="8"
                        refY="5"
                        markerWidth="6"
                        markerHeight="6"
                        orient="auto-start-reverse"
                    >
                        <path d="M 0 0 L 10 5 L 0 10 z" fill="#2563EB" />
                    </marker>
                </defs>

                {/* Lines connecting corners with arrows */}
                {cornerKeys.map((key, index) => {
                    const nextKey = cornerKeys[(index + 1) % cornerKeys.length];
                    const p1 = corners[key];
                    const p2 = corners[nextKey];
                    return (
                        <line
                            key={`${key}-${nextKey}`}
                            x1={toPct(p1.x)}
                            y1={toPct(p1.y)}
                            x2={toPct(p2.x)}
                            y2={toPct(p2.y)}
                            stroke="rgba(59, 130, 246, 0.8)"
                            strokeWidth="2"
                            markerEnd="url(#arrow)"
                        />
                    );
                })}

                {/* Corner Handles */}
                {Object.entries(corners).map(([key, point]) => {
                    let rotation = 0;
                    switch (key) {
                        case 'tl': rotation = -135; break;
                        case 'tr': rotation = -45; break;
                        case 'br': rotation = 45; break;
                        case 'bl': rotation = 135; break;
                    }

                    return (
                        <svg
                            key={key}
                            x={toPct(point.x)}
                            y={toPct(point.y)}
                            style={{ overflow: 'visible' }}
                        >
                            <circle
                                r={HANDLE_RADIUS}
                                fill="white"
                                stroke="#2563EB"
                                strokeWidth="3"
                                style={{ cursor: 'move', transition: 'r 0.1s', touchAction: 'none' }} // Animate radius instead of transform
                                onMouseEnter={(e) => e.currentTarget.setAttribute('r', (HANDLE_RADIUS * 1.2).toString())}
                                onMouseLeave={(e) => e.currentTarget.setAttribute('r', HANDLE_RADIUS.toString())}
                                onMouseDown={handleMouseDown(key as keyof typeof corners)}
                                onTouchStart={handleTouchStart(key as keyof typeof corners)}
                            />
                            <path
                                d="M -5 0 L 5 0 M 1 -4 L 5 0 L 1 4"
                                fill="none"
                                stroke="#2563EB"
                                strokeWidth="2"
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                transform={`rotate(${rotation})`}
                                style={{ pointerEvents: 'none' }}
                            />
                        </svg>
                    );
                })}
            </svg>
        </div>
    );
};
